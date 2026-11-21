import com.sun.tools.attach.VirtualMachine;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * Attach to a First Aid dev client that is joining a singleplayer world. Drives the combat adrenaline limits
 * (adrenalineMaxLevel, attack gain, cooldown, hold) through the real game: /firstaid commands, /damage hits on real
 * husks through each loader's damage events, the real damage model, real mob effects and the persisted config file.
 *
 * Options: result file|config file|join. join is "sp:world" when the probe must open the world itself, empty when
 * quick play already does.
 */
public class AdrenalineRuntimeProbe {
    private static Instrumentation instrumentation;
    private static Object client;
    private static Class<?> minecraftClass;
    private static Object server;
    private static final List<String> results = new ArrayList<>();
    private static long lastHitTick = Long.MIN_VALUE;
    private static final int FAR_POOL = 12;

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }

    public static void agentmain(String options, Instrumentation inst) {
        instrumentation = inst;
        String[] parts = options.split("\\|", -1);
        String resultFile = parts[0];
        Path config = Path.of(parts[1]);
        String join = parts.length > 2 ? parts[2] : "";
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                minecraftClass = loaded("net.minecraft.client.Minecraft");
                client = minecraftClass.getMethod("getInstance").invoke(null);
                if (!join.isEmpty()) join(join);
                waitFor("player joined the world", 240, () -> field(client, "player") != null && field(client, "level") != null);
                onMain(() -> {
                    try { field(client, "options").getClass().getField("pauseOnLostFocus").setBoolean(field(client, "options"), false); }
                    catch (ReflectiveOperationException ignored) { }
                    return null;
                });
                server = call(client, "getSingleplayerServer");
                dismissKnockdownNotice();
                scenarios(config);
                ok = true;
            } catch (Throwable error) {
                results.add("FAIL " + error + "\n" + stack(error));
            } finally {
                try {
                    Files.writeString(Path.of(resultFile), (ok ? "PASS" : "FAIL") + "\n" + String.join("\n", results) + "\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try { onMain(() -> { minecraftClass.getMethod("stop").invoke(client); return null; }); } catch (Throwable ignored) { }
            }
        }, "firstaid-adrenaline-probe");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------------------------------------------------------- scenarios

    private static void scenarios(Path config) throws Exception {
        // Dev run configs carry tuned values from earlier sessions; the launcher restores the file afterwards.
        command("firstaid reset");
        dismissKnockdownNotice(5_000);
        command("gamemode survival @a");
        command("difficulty easy");
        command("time set noon");
        command("weather clear");
        command("kill @e[type=!minecraft:player]");
        command("execute as @p at @s run tp @s ~ ~ ~ -90 0");
        command("execute at @p run fill ~38 ~-1 ~-3 ~45 ~-1 ~3 minecraft:stone");
        command("execute at @p run fill ~38 ~ ~-3 ~45 ~3 ~3 minecraft:air");
        for (int i = 0; i < FAR_POOL; i++) {
            command("execute at @p run summon minecraft:husk ~" + (39 + i % 4 * 2) + " ~ ~" + (i / 4 * 2 - 2)
                    + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"faFar" + i + "\"]}");
        }
        sleep(1500);
        check(countTagged("faFar") == FAR_POOL, "twelve far husks spawned out of sight");
        expectDefaults("defaults before any command");

        // Same-tick hits (a sweep or multishot volley) count as one attack.
        reset();
        double[] sweep = onServer(() -> {
            for (int i = 0; i < 4; i++) damage("faFar" + i);
            return new double[] {intensity(), level()};
        });
        check(near(sweep[0], 0.06), "four same-tick hits count once: intensity " + fmt(sweep[0]) + " (1.3.5 gave 0.2213)");
        check(sweep[1] == 0, "a single counted hit stays below tier I");

        // Default behaviour for one target per tick is unchanged.
        reset();
        perTickHits(30);
        double unchanged = onServer(AdrenalineRuntimeProbe::intensity);
        check(near(unchanged, 1 - Math.pow(0.94, 30)), "30 separate hits at the defaults: intensity " + fmt(unchanged) + " matches 1.3.5");
        check(onServer(AdrenalineRuntimeProbe::level) == 5, "the defaults still reach tier V");
        check(effectLevel("adrenaline_strength") == 5 && effectLevel("adrenaline_speed") == 5 && effectLevel("adrenaline_attack_speed") == 5,
                "tier V applies the speed, attack speed and strength effects at level V");

        // adrenalineMaxLevel caps combat tiers.
        command("firstaid adrenaline maxlevel 2");
        check(configInt("adrenalineMaxLevel") == 2, "/firstaid adrenaline maxlevel 2 updates the server config");
        reset();
        perTickHits(60);
        double cappedTwo = onServer(AdrenalineRuntimeProbe::intensity);
        check(onServer(AdrenalineRuntimeProbe::level) == 2 && cappedTwo <= 0.4901, "60 hits stop at tier II: intensity " + fmt(cappedTwo));
        check(effectLevel("adrenaline_strength") == 2 && effectLevel("adrenaline_speed") == 2, "tier II effects are applied at level II");
        command("firstaid adrenaline maxlevel 0");
        reset();
        perTickHits(60);
        double cappedZero = onServer(AdrenalineRuntimeProbe::intensity);
        check(onServer(AdrenalineRuntimeProbe::level) == 0 && cappedZero <= 0.1901, "maxlevel 0 keeps combat below tier I: intensity " + fmt(cappedZero));
        sleep(300);
        check(effectLevel("adrenaline_strength") == 0 && effectLevel("adrenaline_speed") == 0, "maxlevel 0 applies no combat effects");
        command("firstaid adrenaline maxlevel 6");
        check(configInt("adrenalineMaxLevel") == 0, "an out-of-range maxlevel is rejected");

        // The cap never lowers stronger sources such as the adrenaline injector.
        command("firstaid adrenaline maxlevel 1");
        reset();
        onServer(() -> call(model(), "applyAdrenalineInjection", player()), true);
        int injectedHold = onServer(AdrenalineRuntimeProbe::hold);
        perTickHits(10);
        check(onServer(AdrenalineRuntimeProbe::level) == 5 && onServer(AdrenalineRuntimeProbe::intensity) > 0.99,
                "an injector keeps tier V while hits are capped at tier I");
        int heldAfterHits = onServer(AdrenalineRuntimeProbe::hold);
        check(heldAfterHits < injectedHold, "hits above the cap do not extend the injector hold (" + injectedHold + " -> " + heldAfterHits + " ticks)");
        command("firstaid adrenaline maxlevel 5");

        // Attack gain multiplier.
        command("firstaid adrenaline attackgain 0");
        check(configDouble("adrenalineAttackGainMultiplier") == 0.0, "/firstaid adrenaline attackgain 0 updates the server config");
        reset();
        perTickHits(20);
        check(onServer(AdrenalineRuntimeProbe::intensity) == 0.0 && onServer(AdrenalineRuntimeProbe::hold) == 0,
                "attackgain 0 disables hit adrenaline without freezing decay");
        command("firstaid adrenaline attackgain 0.5");
        reset();
        double halved = onServer(() -> { damage("faFar0"); return intensity(); });
        check(near(halved, 0.03), "attackgain 0.5 halves one hit: intensity " + fmt(halved));
        command("firstaid adrenaline attackgain 1");

        // Cooldown between counted hits.
        command("firstaid adrenaline attackcooldown 10");
        check(configInt("adrenalineAttackCooldownTicks") == 10, "/firstaid adrenaline attackcooldown 10 updates the server config");
        reset();
        perTickHits(20);
        double cooled = onServer(AdrenalineRuntimeProbe::intensity);
        long counted = Math.round(Math.log(1 - cooled) / Math.log(0.94));
        check(counted >= 2 && counted <= 3, "20 hits over 20 ticks with a 10 tick cooldown count " + counted + " times");
        command("firstaid adrenaline attackcooldown 0");

        // Hold time after a hit.
        command("firstaid adrenaline attackhold 0");
        reset();
        int noHold = onServer(() -> { damage("faFar1"); return hold(); });
        check(noHold == 0, "attackhold 0 lets adrenaline decay right after a hit");
        command("firstaid adrenaline attackhold 40");
        sleep(700);
        reset();
        int defaultHold = onServer(() -> { damage("faFar2"); return hold(); });
        check(defaultHold == 40, "attackhold 40 holds for 40 ticks");

        // Persistence of every new key.
        command("firstaid adrenaline maxlevel 3");
        command("firstaid adrenaline attackgain 0.75");
        command("firstaid adrenaline attackcooldown 5");
        command("firstaid adrenaline attackhold 30");
        sleep(500);
        String saved = Files.readString(config, StandardCharsets.UTF_8);
        for (String entry : new String[] {"adrenalineMaxLevel\\W+3", "adrenalineAttackGainMultiplier\\W+0\\.75",
                "adrenalineAttackCooldownTicks\\W+5", "adrenalineAttackHoldTicks\\W+30"}) {
            check(java.util.regex.Pattern.compile(entry).matcher(saved).find(), "config file persists " + entry.replace("\\W+", " = ").replace("\\.", "."));
        }

        // Realistic horde: four visible husks hit together every 12 ticks (sword cooldown), 25 seconds.
        // NeoForge's config watcher reloads the file about a second after each save; let it settle first.
        sleep(3000);
        command("firstaid reset");
        sleep(2500);
        expectDefaults("/firstaid reset restores the defaults");
        dismissKnockdownNotice(5_000);
        command("execute at @p run fill ~2 ~-1 ~-3 ~6 ~-1 ~3 minecraft:stone");
        command("execute at @p run fill ~2 ~ ~-3 ~6 ~3 ~3 minecraft:air");
        for (int i = 0; i < 4; i++) {
            command("execute at @p run summon minecraft:husk ~4 ~ ~" + (i * 1.2 - 1.8) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"faNear" + i + "\"]}");
        }
        sleep(1000);
        horde("default settings", 25, 5);
        screenshot("adrenaline-horde-default");
        command("firstaid adrenaline maxlevel 2");
        command("firstaid adrenaline attackgain 0.5");
        horde("maxlevel 2 + attackgain 0.5", 25, 2);
        screenshot("adrenaline-horde-capped");
        command("firstaid reset");
        command("kill @e[type=minecraft:husk]");
    }

    private static void horde(String label, int seconds, int allowedLevel) throws Exception {
        reset();
        StringBuilder timeline = new StringBuilder();
        long start = onServer(AdrenalineRuntimeProbe::gameTime);
        int previous = -1, highest = 0;
        long firstMax = -1;
        while (true) {
            long[] state = onServer(() -> {
                long now = gameTime();
                if (lastHitTick != Long.MIN_VALUE && now < lastHitTick + 12) return null;
                lastHitTick = now;
                for (int i = 0; i < 4; i++) damage("faNear" + i);
                return new long[] {now, level(), Math.round(intensity() * 1000)};
            }, true);
            if (state == null) { Thread.sleep(5); continue; }
            double elapsed = (state[0] - start) / 20.0;
            if (state[1] != previous) {
                timeline.append(String.format(Locale.ROOT, " %.1fs->T%d", elapsed, state[1]));
                previous = (int) state[1];
            }
            highest = Math.max(highest, (int) state[1]);
            if (state[1] == allowedLevel && firstMax < 0) firstMax = state[0] - start;
            if (elapsed >= seconds) break;
        }
        results.add("DATA horde " + label + ":" + timeline);
        check(highest <= allowedLevel, "horde with " + label + " never exceeds tier " + allowedLevel + " (highest " + highest + ")");
        check(firstMax >= 0, "horde with " + label + " reaches tier " + allowedLevel + " after " + String.format(Locale.ROOT, "%.1f", firstMax / 20.0) + " s");
    }

    private static void expectDefaults(String label) throws Exception {
        check(configInt("adrenalineMaxLevel") == 5 && configDouble("adrenalineAttackGainMultiplier") == 1.0
                && configInt("adrenalineAttackCooldownTicks") == 0 && configInt("adrenalineAttackHoldTicks") == 40, label + " (5, 1.0, 0, 40)");
    }

    // ---------------------------------------------------------------- game access

    private static void perTickHits(int count) throws Exception {
        int done = 0;
        while (done < count) {
            int index = done;
            Boolean hit = onServer(() -> {
                long now = gameTime();
                if (now <= lastHitTick) return false;
                lastHitTick = now;
                damage("faFar" + index % FAR_POOL);
                return true;
            });
            if (hit) done++; else Thread.sleep(5);
        }
    }

    private static void reset() throws Exception {
        onServer(() -> {
            call(model(), "clearStatusEffects");
            run("effect clear @p");
            return null;
        }, true);
        sleep(1100);
        lastHitTick = Long.MIN_VALUE;
    }

    /** Runs on the server thread. */
    private static void damage(String tag) throws Exception {
        run("damage @e[tag=" + tag + ",limit=1] 0.01 minecraft:player_attack by @p");
    }

    private static void run(String command) throws Exception {
        call(call(server, "getCommands"), "performPrefixedCommand", call(server, "createCommandSourceStack"), command);
    }

    private static void command(String command) throws Exception {
        onServer(() -> { run(command); return null; }, true);
        results.add("RUN /" + command);
        // Each /firstaid setting saves the config file; Forge and NeoForge reload it about a second later and a
        // reload of an older save can overwrite a newer setting. Space settings like an admin typing them.
        if (command.startsWith("firstaid ")) sleep(1500);
    }

    private static long gameTime() throws Exception {
        return (long) call(call(server, "overworld"), "getGameTime");
    }

    private static Object player() throws Exception {
        return ((List<?>) call(call(server, "getPlayerList"), "getPlayers")).get(0);
    }

    private static Object model() throws Exception {
        Object player = player();
        for (Method method : loaded("ichttt.mods.firstaid.common.util.CommonUtils").getMethods()) {
            if (method.getName().equals("getDamageModel") && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(player)) {
                return method.invoke(null, player);
            }
        }
        throw new IllegalStateException("CommonUtils.getDamageModel not found");
    }

    private static double intensity() throws Exception { return ((Number) call(model(), "getSuppressionIntensity")).doubleValue(); }
    private static int level() throws Exception { return (int) call(model(), "getAdrenalineLevel"); }
    private static int hold() throws Exception { return (int) call(model(), "getSuppressionHoldTicks"); }

    private static int countTagged(String prefix) throws Exception {
        return onServer(() -> {
            int count = 0;
            for (int i = 0; i < FAR_POOL; i++) {
                Object source = call(server, "createCommandSourceStack");
                Object dispatcher = call(call(server, "getCommands"), "getDispatcher");
                Object parsed = call(dispatcher, "parse", "execute if entity @e[tag=" + prefix + i + "]", source);
                try {
                    count += (int) call(dispatcher, "execute", parsed);
                } catch (Exception missing) {
                    // a failed condition throws; the husk is absent
                }
            }
            return count;
        });
    }

    /** Effect level (amplifier + 1) of a First Aid effect on the server player, 0 when absent. */
    private static int effectLevel(String name) throws Exception {
        return onServer(() -> {
            for (Object instance : (Collection<?>) call(player(), "getActiveEffects")) {
                if (String.valueOf(call(instance, "getDescriptionId")).endsWith("firstaid." + name)) {
                    return (int) call(instance, "getAmplifier") + 1;
                }
            }
            return 0;
        });
    }

    private static Object configValue(String key) throws Exception {
        Object serverConfig = loaded("ichttt.mods.firstaid.FirstAidConfig").getField("SERVER").get(null);
        return call(serverConfig.getClass().getField(key).get(serverConfig), "get");
    }

    private static int configInt(String key) throws Exception { return onServer(() -> ((Number) configValue(key)).intValue()); }
    private static double configDouble(String key) throws Exception { return onServer(() -> ((Number) configValue(key)).doubleValue()); }

    private static void dismissKnockdownNotice() throws Exception {
        dismissKnockdownNotice(20_000);
    }

    /** /firstaid reset may change the knockdown rules of the run config, which reopens the notice. */
    private static void dismissKnockdownNotice(long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            if (onMain(() -> { Object s = screen(); return s != null && s.getClass().getName().endsWith("KnockdownNoticeScreen"); })) {
                onMain(() -> {
                    for (Object child : (List<?>) call(screen(), "children")) {
                        if (hasSuperclass(child.getClass(), "AbstractButton")) {
                            for (Method method : allMethods(child.getClass())) {
                                if (method.getName().equals("onPress")) {
                                    method.setAccessible(true);
                                    method.invoke(child, new Object[method.getParameterCount()]);
                                    return null;
                                }
                            }
                        }
                    }
                    return null;
                });
                results.add("RUN confirmed the knockdown notice");
                sleep(1000);
                return;
            }
            Thread.sleep(250);
        }
    }

    private static void join(String target) throws Exception {
        waitFor("title screen ready", 240, () -> {
            Object screen = screen();
            return overlay() == null && screen != null && screen.getClass().getName().endsWith("TitleScreen");
        });
        onMain(() -> {
            Object title = screen();
            Object flows = call(client, "createWorldOpenFlows");
            try {
                call(flows, "openWorld", target.substring(3), (Runnable) () -> { });
            } catch (NoSuchMethodException e) {
                call(flows, "loadLevel", title, target.substring(3));
            }
            return null;
        });
        results.add("RUN join " + target);
    }

    private static Object overlay() {
        try {
            try { return call(client, "getOverlay"); }
            catch (NoSuchMethodException e) { return call(minecraftClass.getField("gui").get(client), "overlay"); }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object screen() {
        try {
            try { return minecraftClass.getField("screen").get(client); }
            catch (NoSuchFieldException e) { return call(minecraftClass.getField("gui").get(client), "screen"); }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void screenshot(String tag) {
        try {
            sleep(1500);
            onMain(() -> {
                Class<?> screenshot = loaded("net.minecraft.client.Screenshot");
                for (Method method : screenshot.getMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    if (method.getName().equals("grab") && types.length >= 4 && types[0] == File.class && types[1] == String.class) {
                        Object[] args = new Object[types.length];
                        args[0] = field(client, "gameDirectory");
                        args[1] = tag + ".png";
                        args[2] = renderTarget(types[2]);
                        for (int i = 3; i < types.length; i++) {
                            args[i] = types[i] == int.class ? (Object) 1 : (java.util.function.Consumer<Object>) message -> { };
                        }
                        method.invoke(null, args);
                        return null;
                    }
                }
                throw new IllegalStateException("no Screenshot.grab overload");
            });
            results.add("SHOT " + tag);
        } catch (Throwable error) {
            results.add("NOTE screenshot " + tag + " failed: " + error);
        }
    }

    private static Object renderTarget(Class<?> type) throws Exception {
        for (Object owner : new Object[] {client, field(client, "gameRenderer")}) {
            for (Method method : allMethods(owner.getClass())) {
                if (method.getParameterCount() == 0 && type.isAssignableFrom(method.getReturnType())
                        && !Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    Object target = method.invoke(owner);
                    if (target != null) return target;
                }
            }
        }
        throw new IllegalStateException("no " + type.getName() + " on Minecraft or GameRenderer");
    }

    // ---------------------------------------------------------------- helpers

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.002;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        results.add("PASS " + message);
    }

    private static void waitFor(String what, int seconds, BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            Boolean ok = onMain(condition::getAsBoolean);
            if (ok) return;
            Thread.sleep(250);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        minecraftClass.getMethod("execute", Runnable.class).invoke(client, (Runnable) () -> {
            try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future.get(30, TimeUnit.SECONDS);
    }

    private static <T> T onServer(Callable<T> action) throws Exception {
        return onServer(action, false);
    }

    private static <T> T onServer(Callable<T> action, boolean allowNull) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        call(server, "execute", (Runnable) () -> {
            try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        T value = future.get(30, TimeUnit.SECONDS);
        if (value == null && !allowNull) throw new IllegalStateException("server task returned no value");
        return value;
    }

    private static Class<?> loaded(String name) {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type.getName().equals(name)) return type;
        }
        try { return client.getClass().getClassLoader().loadClass(name); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(name + " is not loaded", e); }
    }

    private static Object field(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : allMethods(target.getClass())) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length && accepts(method, args)) {
                method.setAccessible(true);
                try { return method.invoke(target, args); }
                catch (InvocationTargetException e) { throw e.getCause() instanceof Exception cause ? cause : e; }
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
    }

    private static boolean accepts(Method method, Object[] args) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null && !wrap(types[i]).isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        return type == boolean.class ? Boolean.class : type == int.class ? Integer.class : type == float.class ? Float.class
                : type == double.class ? Double.class : type == long.class ? Long.class : Object.class;
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            for (Class<?> iface : current.getInterfaces()) methods.addAll(Arrays.asList(iface.getMethods()));
        }
        return methods;
    }

    private static boolean hasSuperclass(Class<?> type, String simpleName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static String stack(Throwable error) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) builder.append("  at ").append(element).append('\n');
        if (error.getCause() != null) builder.append("caused by ").append(error.getCause()).append('\n').append(stack(error.getCause()));
        return builder.toString();
    }
}
