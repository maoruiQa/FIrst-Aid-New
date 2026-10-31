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
 * Attach only to a First Aid dev client that is joining a world. Drives the knockdown notice and knockdown modes
 * through the real game (integrated server commands, real damage pipeline, real screens), then exits the client.
 *
 * Options: phase|result file|status file|ack store|expected mode|join. Phases: first, seen, config, remote1, remote2.
 * join is "sp:world" or "mp:host:port" when the probe must open the world itself, empty when quick play already does.
 * Remote phases ask the launcher to run server commands by writing "REQ name" to the status file and waiting for
 * "DONE name".
 */
public class KnockdownRuntimeProbe {
    private static Instrumentation instrumentation;
    private static Object client;
    private static Class<?> minecraftClass;
    private static final List<String> results = new ArrayList<>();
    private static String statusFile;

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }

    public static void agentmain(String options, Instrumentation inst) {
        instrumentation = inst;
        String[] parts = options.split("\\|", -1);
        String phase = parts[0], resultFile = parts[1];
        statusFile = parts[2];
        Path store = Path.of(parts[3]);
        String expected = parts[4];
        String join = parts.length > 5 ? parts[5] : "";
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                minecraftClass = loaded("net.minecraft.client.Minecraft");
                client = minecraftClass.getMethod("getInstance").invoke(null);
                if (!join.isEmpty()) join(join);
                waitFor("player joined the world", 240, () -> field(client, "player") != null && field(client, "level") != null);
                switch (phase) {
                    case "first" -> first(store);
                    case "seen" -> seen();
                    case "config" -> expectNoticeAndConfirm(expected, store, "config-file change");
                    case "remote1" -> remote1(store);
                    case "remote2" -> expectNoticeAndConfirm(expected, store, "offline admin change");
                    default -> throw new IllegalArgumentException("unknown phase " + phase);
                }
                ok = true;
            } catch (Throwable error) {
                results.add("FAIL " + error + "\n" + stack(error));
            } finally {
                try {
                    Files.writeString(Path.of(resultFile), (ok ? "PASS" : "FAIL") + " " + phase + "\n" + String.join("\n", results) + "\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try { onMain(() -> { minecraftClass.getMethod("stop").invoke(client); return null; }); } catch (Throwable ignored) { }
            }
        }, "firstaid-knockdown-probe");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------------------------------------------------------- phases

    private static void first(Path store) throws Exception {
        // food regeneration heals the emptied head within a second and ends the downed state, so keep it out of the test
        command("gamerule naturalRegeneration false");
        command("gamerule natural_health_regeneration false");
        expectNoticeAndConfirm("CHANCE", store, "first join");
        check(Math.abs((double) call(call(screenOrLast(), "notice"), "coefficient") - 0.35D) < 1e-9, "default coefficient 0.35 shown");

        command("firstaid knockdown always");
        expectNoticeAndConfirm("ALWAYS", store, "online admin change");
        hitHead(100);
        check(downed() && serverPlayerAlive(), "ALWAYS: a 100 damage head hit downs instead of killing" + state());

        command("firstaid knockdown never");
        expectNoticeAndConfirm("NEVER", store, "admin change while the player is down");
        sleep(1000);
        check(downed() && serverPlayerAlive(), "switching to NEVER keeps an already downed player alive" + state());
        killAndRespawn();

        hitHead(100);
        waitFor("NEVER: head hit kills", 10, () -> !serverPlayerAlive());
        check(!downed(), "NEVER: the player was not downed");
        respawn();

        command("firstaid knockdown chance");
        expectNoticeAndConfirm("CHANCE", store, "switch back to chance");
        command("firstaid knockdown coefficient 0");
        expectNoticeAndConfirm("CHANCE", store, "coefficient change in chance mode");
        hitHead(100);
        check(downed() && serverPlayerAlive(), "CHANCE with coefficient 0 always downs" + state());
        killAndRespawn();

        command("firstaid knockdown coefficient 5");
        expectNoticeAndConfirm("CHANCE", store, "coefficient 5");
        hitHead(100);
        waitFor("CHANCE with coefficient 5 and 24x overflow kills", 10, () -> !serverPlayerAlive());
        respawn();

        hitHead(headMax());
        check(downed() && serverPlayerAlive(), "CHANCE with coefficient 5 still downs an exact (no overflow) head hit" + state());
        killAndRespawn();

        command("firstaid knockdown always");
        expectNoticeAndConfirm("ALWAYS", store, "back to always");
        command("firstaid knockdown coefficient 2");
        sleep(3000);
        check(!noticeShown() && !(boolean) clientStatic("hasPending"), "coefficient change outside chance mode shows no notice");

        command("firstaid knockdown chance");
        command("firstaid knockdown coefficient 0.35");
        waitFor("latest notice replaces the earlier one", 20, () -> noticeShown() && "CHANCE".equals(noticeMode())
                && Math.abs((double) callUnchecked(callUnchecked(screen(), "notice"), "coefficient") - 0.35D) < 1e-9);
        confirm();
        sleep(3000);
        check(!noticeShown(), "only one notice remains after two quick changes");
        checkStore(store, "CHANCE;k=0.35");
    }

    private static void seen() throws Exception {
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            check(!noticeShown(), "no notice for a player who already acknowledged these settings");
            sleep(500);
        }
        results.add("PASS no notice within 20 s of rejoining with unchanged settings");
    }

    private static void remote1(Path store) throws Exception {
        expectNoticeAndConfirm("CHANCE", store, "dedicated server first join");
        request("never");
        expectNoticeAndConfirm("NEVER", store, "dedicated server console change");
        request("damage");
        waitFor("dedicated NEVER: head hit shows the death screen", 15, () -> {
            Object screen = screen();
            return screen != null && screen.getClass().getName().endsWith("DeathScreen");
        });
        respawn();
        results.add("PASS dedicated server: death instead of downed in NEVER mode");
    }

    // ---------------------------------------------------------------- steps

    private static void expectNoticeAndConfirm(String mode, Path store, String label) throws Exception {
        waitFor("notice shown (" + label + ")", 60, () -> noticeShown() && mode.equals(noticeMode()));
        Object screen = screen();
        check(!(boolean) call(screen, "shouldCloseOnEsc"), label + ": Escape cannot dismiss the notice");
        check(!(boolean) call(screen, "isPauseScreen"), label + ": the notice does not pause the game");
        sleep(800);
        screenshot("knockdown-" + label.replace(' ', '-') + "-" + mode.toLowerCase(Locale.ROOT));
        lastScreen = screen;
        confirm();
        waitFor("notice closed after confirm (" + label + ")", 10, () -> !noticeShown());
        String fingerprint = mode.equals("CHANCE") ? "CHANCE;k=" : mode;
        waitFor("acknowledgement stored (" + label + ")", 10, () -> storeContains(store, fingerprint));
        results.add("PASS " + label + ": " + mode + " notice shown, confirmed and acknowledged");
    }

    private static Object lastScreen;

    private static Object screenOrLast() throws Exception {
        Object screen = screen();
        return screen != null && screen.getClass().getName().endsWith("KnockdownNoticeScreen") ? screen : lastScreen;
    }

    private static void confirm() throws Exception {
        onMain(() -> {
            Object screen = screen();
            for (Object child : (List<?>) call(screen, "children")) {
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
            throw new IllegalStateException("no confirm button on " + screen.getClass().getName());
        });
    }

    private static void killAndRespawn() throws Exception {
        command("kill @a");
        waitFor("player died", 10, () -> !serverPlayerAlive());
        respawn();
    }

    private static void respawn() throws Exception {
        waitFor("death screen", 15, () -> {
            Object screen = screen();
            return screen != null && screen.getClass().getName().endsWith("DeathScreen");
        });
        onMain(() -> call(field(client, "player"), "respawn"));
        waitFor("respawned", 20, () -> {
            Object player = field(client, "player");
            Object screen = screen();
            return player != null && (boolean) callUnchecked(player, "isAlive") && (screen == null || !screen.getClass().getName().endsWith("DeathScreen"));
        });
        sleep(1500);
    }

    private static void command(String command) throws Exception {
        Object server = call(client, "getSingleplayerServer");
        CompletableFuture<Void> done = new CompletableFuture<>();
        call(server, "execute", (Runnable) () -> {
            try {
                Object source = call(server, "createCommandSourceStack");
                Object commands = call(server, "getCommands");
                call(commands, "performPrefixedCommand", source, command);
                done.complete(null);
            } catch (Throwable error) {
                done.completeExceptionally(error);
            }
        });
        done.get(20, TimeUnit.SECONDS);
        results.add("RUN /" + command);
    }

    private static void request(String name) throws Exception {
        Files.writeString(Path.of(statusFile), "REQ " + name);
        waitFor("launcher handled " + name, 60, () -> {
            try { return Files.readString(Path.of(statusFile)).trim().equals("DONE " + name); }
            catch (Exception e) { return false; }
        });
        results.add("RUN remote " + name);
    }

    private static void join(String target) throws Exception {
        waitFor("title screen ready", 240, () -> {
            Object screen = screen();
            return overlay() == null && screen != null && screen.getClass().getName().endsWith("TitleScreen");
        });
        onMain(() -> {
            Object title = screen();
            if (target.startsWith("sp:")) {
                Object flows = call(client, "createWorldOpenFlows");
                try {
                    call(flows, "openWorld", target.substring(3), (Runnable) () -> { });
                } catch (NoSuchMethodException e) {
                    call(flows, "loadLevel", title, target.substring(3));
                }
            } else {
                ClassLoader loader = client.getClass().getClassLoader();
                Object address = loader.loadClass("net.minecraft.client.multiplayer.resolver.ServerAddress")
                        .getMethod("parseString", String.class).invoke(null, target.substring(3));
                Object data = null;
                for (Constructor<?> constructor : loader.loadClass("net.minecraft.client.multiplayer.ServerData").getConstructors()) {
                    Class<?>[] types = constructor.getParameterTypes();
                    if (types.length == 3 && types[0] == String.class && types[1] == String.class) {
                        Object third = types[2] == boolean.class ? false : Enum.valueOf((Class) types[2], "OTHER");
                        data = constructor.newInstance("KnockdownQA", target.substring(3), third);
                    }
                }
                for (Method method : loader.loadClass("net.minecraft.client.gui.screens.ConnectScreen").getMethods()) {
                    if (!method.getName().equals("startConnecting")) continue;
                    Object[] args = new Object[method.getParameterCount()];
                    Class<?>[] types = method.getParameterTypes();
                    for (int i = 0; i < args.length; i++) {
                        if (types[i].isInstance(title)) args[i] = title;
                        else if (types[i].isInstance(client)) args[i] = client;
                        else if (types[i].isInstance(address)) args[i] = address;
                        else if (data != null && types[i].isInstance(data)) args[i] = data;
                        else if (types[i] == boolean.class) args[i] = false;
                    }
                    method.invoke(null, args);
                    return null;
                }
                throw new IllegalStateException("ConnectScreen.startConnecting not found");
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

    // ---------------------------------------------------------------- state

    private static boolean noticeShown() {
        Object screen = screen();
        return screen != null && screen.getClass().getName().endsWith("KnockdownNoticeScreen");
    }

    private static String noticeMode() {
        return String.valueOf(callUnchecked(callUnchecked(screen(), "notice"), "mode"));
    }

    private static Object screen() {
        try {
            try { return minecraftClass.getField("screen").get(client); }
            catch (NoSuchFieldException e) { return call(minecraftClass.getField("gui").get(client), "screen"); }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object clientStatic(String method) throws Exception {
        return loaded("ichttt.mods.firstaid.client.KnockdownNoticeClient").getMethod(method).invoke(null);
    }

    private static Object serverPlayer() throws Exception {
        Object server = call(client, "getSingleplayerServer");
        List<?> players = (List<?>) call(call(server, "getPlayerList"), "getPlayers");
        return players.get(0);
    }

    private static boolean serverPlayerAlive() {
        try { return (boolean) call(serverPlayer(), "isAlive"); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static Object serverModel() throws Exception {
        Object player = serverPlayer();
        Class<?> utils = loaded("ichttt.mods.firstaid.common.util.CommonUtils");
        for (Method method : utils.getMethods()) {
            if (method.getName().equals("getDamageModel") && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(player)) {
                return method.invoke(null, player);
            }
        }
        throw new IllegalStateException("CommonUtils.getDamageModel not found");
    }

    private static int headMax() throws Exception {
        Object model = serverModel();
        return (int) call(model.getClass().getField("HEAD").get(model), "getMaxHealth");
    }

    private static boolean downed() throws Exception {
        Object model = serverModel();
        // Forge invalidates the capability of a dead player, so a missing model means not downed
        return model != null && (boolean) call(model, "isCriticalDowned");
    }

    /** Server-side state, appended to damage checks so a failure says what actually happened. */
    private static String state() {
        try {
            Object player = serverPlayer();
            Object model = serverModel();
            Object head = model.getClass().getField("HEAD").get(model);
            Object body = model.getClass().getField("BODY").get(model);
            Class<?> firstAid = loaded("ichttt.mods.firstaid.FirstAid");
            return " [alive=" + call(player, "isAlive") + " health=" + call(player, "getHealth")
                    + " head=" + head.getClass().getField("currentHealth").get(head) + "/" + call(head, "getMaxHealth")
                    + " body=" + body.getClass().getField("currentHealth").get(body) + "/" + call(body, "getMaxHealth")
                    + " critical=" + call(model, "isCriticalConditionActive") + " unconscious=" + call(model, "isUnconscious")
                    + " downed=" + call(model, "isCriticalDowned") + " mode=" + firstAid.getField("knockdownMode").get(null)
                    + " k=" + firstAid.getField("knockdownOverflowCoefficient").get(null) + "]";
        } catch (Throwable error) {
            return " [state unavailable: " + error + "]";
        }
    }

    private static void hitHead(Object damage) throws Exception {
        command("execute as @a run damagePart HEAD " + damage);
        sleep(1000);
        results.add("STATE after head hit " + damage + ":" + state());
    }

    private static boolean storeContains(Path store, String fingerprint) {
        try {
            Object player = field(client, "player");
            String uuid = String.valueOf(call(player, "getUUID"));
            String text = Files.readString(store, StandardCharsets.UTF_8);
            int at = text.indexOf("\"" + uuid + "\"");
            return at >= 0 && text.indexOf(fingerprint, at) > at && text.indexOf(fingerprint, at) < text.indexOf('\n', at);
        } catch (Exception e) {
            return false;
        }
    }

    private static void checkStore(Path store, String fingerprint) throws Exception {
        waitFor("store holds " + fingerprint, 10, () -> storeContains(store, fingerprint));
        results.add("PASS store holds " + fingerprint);
    }

    private static void screenshot(String tag) {
        try {
            onMain(() -> {
                Class<?> screenshot = loaded("net.minecraft.client.Screenshot");
                // prefer the overload taking a file name, so each screenshot is named after its scenario
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
                for (Method method : screenshot.getMethods()) {
                    if (method.getName().equals("grab") && method.getParameterCount() == 2) {
                        method.invoke(null, client, false);
                        return null;
                    }
                }
                throw new IllegalStateException("no Screenshot.grab overload");
            });
            results.add("SHOT " + tag + " " + System.currentTimeMillis());
        } catch (Throwable error) {
            results.add("NOTE screenshot " + tag + " failed: " + error);
        }
    }

    /** The main render target, found by type because its accessor was renamed across versions. */
    private static Object renderTarget(Class<?> type) throws Exception {
        // Minecraft.getMainRenderTarget() up to 1.21.x, GameRenderer.mainRenderTarget() on 26.x
        for (Object owner : new Object[] {client, field(client, "gameRenderer")}) {
            for (Method method : allMethods(owner.getClass())) {
                if (method.getParameterCount() == 0 && type.isAssignableFrom(method.getReturnType())
                        && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    Object target = method.invoke(owner);
                    if (target != null) return target;
                }
            }
        }
        throw new IllegalStateException("no " + type.getName() + " on Minecraft or GameRenderer");
    }

    // ---------------------------------------------------------------- reflection

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

    private static Object callUnchecked(Object target, String name, Object... args) {
        try { return call(target, name, args); } catch (Exception e) { throw new IllegalStateException(e); }
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
