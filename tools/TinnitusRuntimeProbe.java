import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Attach only to a First Aid dev client. Checks sound, commands and camera hooks, then exits. */
public class TinnitusRuntimeProbe {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }

    public static void agentmain(String output, Instrumentation instrumentation) {
        Thread worker = new Thread(() -> {
            try {
                Class<?> minecraft = Arrays.stream(instrumentation.getAllLoadedClasses())
                        .filter(c -> c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();
                Object client = minecraft.getMethod("getInstance").invoke(null);
                CompletableFuture<Void> done = new CompletableFuture<>();
                minecraft.getMethod("execute", Runnable.class).invoke(client, (Runnable) () -> {
                    try {
                        String result = check(client);
                        Files.writeString(Path.of(output), result);
                        done.complete(null);
                    } catch (Throwable error) {
                        done.completeExceptionally(error);
                    }
                });
                try { done.get(30, TimeUnit.SECONDS); }
                finally {
                    minecraft.getMethod("execute", Runnable.class).invoke(client, (Runnable) () -> {
                        try { minecraft.getMethod("stop").invoke(client); } catch (Exception e) { throw new RuntimeException(e); }
                    });
                }
            } catch (Throwable error) {
                try { Files.writeString(Path.of(output), "FAIL " + error + "\n" + stack(error)); }
                catch (Exception ignored) { error.printStackTrace(); }
            }
        }, "firstaid-runtime-probe");
        worker.setDaemon(true);
        worker.start();
    }

    private static String check(Object client) throws Exception {
        ClassLoader loader = client.getClass().getClassLoader();
        Class<?> controller = loader.loadClass("ichttt.mods.firstaid.client.TinnitusSoundController");
        Class<?> sound = loader.loadClass("ichttt.mods.firstaid.client.TinnitusSoundController$TinnitusSound");
        Object holder = loader.loadClass("ichttt.mods.firstaid.common.RegistryObjects").getField("TINNITUS").get(null);
        Object event;
        try { event = holder.getClass().getMethod("get").invoke(holder); }
        catch (NoSuchMethodException e) { event = holder.getClass().getMethod("value").invoke(holder); }
        Object manager = client.getClass().getMethod("getSoundManager").invoke(client);
        Class<?> soundInstance = loader.loadClass("net.minecraft.client.resources.sounds.SoundInstance");
        Class<?> sourceType = loader.loadClass("net.minecraft.sounds.SoundSource");
        Constructor<?> constructor = sound.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Field remaining = sound.getDeclaredField("remainingTicks");
        remaining.setAccessible(true);
        Field active = controller.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        Method play = manager.getClass().getMethod("play", soundInstance);
        Method stop = manager.getClass().getMethod("stop", soundInstance);
        Method clear = controller.getMethod("clear");
        Method duration = controller.getDeclaredMethod("durationTicks", float.class, int.class);
        duration.setAccessible(true);
        StringBuilder result = new StringBuilder();
        String ui;
        try { sourceType.getField("UI"); ui = "UI"; } catch (NoSuchFieldException e) { ui = "MASTER"; }
        for (String category : List.of(ui, "PLAYERS")) {
            Object source = Enum.valueOf((Class) sourceType, category);
            for (int percent : new int[]{1, 75, 100, 400}) {
                boolean relative = !category.equals("PLAYERS");
                Object instance = constructor.newInstance(event, source, 0.05F, 1.0F, relative, 2.0D, 3.0D, 4.0D, percent);
                if ((int) remaining.get(instance) != (int) duration.invoke(null, 1.0F, percent)) throw new AssertionError("duration");
                play.invoke(manager, instance); // Uses real registry resolution and the native sound engine.
                try {
                    if (soundInstance.getMethod("getSound").invoke(instance) == null) throw new AssertionError("unresolved sound");
                    if ((float) soundInstance.getMethod("getVolume").invoke(instance) <= 0) throw new AssertionError("volume");
                    if ((float) soundInstance.getMethod("getPitch").invoke(instance) != 1.0F) throw new AssertionError("pitch changed");
                    if ((boolean) soundInstance.getMethod("isLooping").invoke(instance) != (percent > 100)) throw new AssertionError("loop");
                    if ((boolean) soundInstance.getMethod("isRelative").invoke(instance) != relative) throw new AssertionError("position");
                    ((List) active.get(null)).add(instance);
                    clear.invoke(null);
                    if (!((List) active.get(null)).isEmpty()) throw new AssertionError("clear");
                    result.append("PASS native sound ").append(category).append(' ').append(percent).append("%\n");
                } finally { stop.invoke(manager, instance); }
            }
        }
        for (int invalid : new int[]{0, 401}) {
            try { duration.invoke(null, 1.0F, invalid); throw new AssertionError("invalid duration accepted"); }
            catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
            }
        }
        result.append("PASS native duration boundaries and sound cleanup\n");
        checkCommands(loader, client, result);
        checkCamera(loader, client, result);
        checkSuppression(loader, client, result);
        checkInjuryData(loader, result);
        return result.toString();
    }

    private static void checkSuppression(ClassLoader loader, Object client, StringBuilder result) throws Exception {
        // NeoForge loads SERVER config on joining a world. Use defaults in memory for this title-screen probe.
        Object spec=null; Method unload=null;
        try {
            Class<?> specType=loader.loadClass("net.neoforged.neoforge.common.ModConfigSpec");
            Field serverSpec=loader.loadClass("ichttt.mods.firstaid.FirstAidConfig").getDeclaredField("serverSpec");
            serverSpec.setAccessible(true); spec=serverSpec.get(null);
            if (!(boolean)specType.getMethod("isLoaded").invoke(spec)) {
                Object config=loader.loadClass("com.electronwill.nightconfig.core.CommentedConfig").getMethod("inMemory").invoke(null);
                Class<?> configType=loader.loadClass("com.electronwill.nightconfig.core.CommentedConfig");
                specType.getMethod("correct",configType).invoke(spec,config);
                Method accept=Arrays.stream(specType.getMethods()).filter(m -> m.getName().equals("acceptConfig")).findFirst().orElseThrow();
                Constructor<?> loadedCtor=loader.loadClass("net.neoforged.fml.config.LoadedConfig")
                        .getDeclaredConstructor(configType,Path.class,loader.loadClass("net.neoforged.fml.config.ModConfig"));
                loadedCtor.setAccessible(true); Object loaded=loadedCtor.newInstance(config,null,null);
                accept.invoke(spec,loaded); unload=accept;
            }
        } catch (ClassNotFoundException ignored) {} // Fabric and Forge already expose defaults before joining.
        try {
            Class<?> modelType = loader.loadClass("ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel");
            Object model = modelType.getConstructor().newInstance();
            Method max = modelType.getDeclaredMethod("forceMaxSuppression", loader.loadClass("net.minecraft.world.entity.player.Player"));
            max.setAccessible(true);
            Field intensity = modelType.getDeclaredField("suppressionIntensity"), hold = modelType.getDeclaredField("suppressionHoldTicks");
            intensity.setAccessible(true); hold.setAccessible(true);
            for (float pressure : new float[]{0,0.2F,0.9F,1}) for (int previousHold : new int[]{0,40,400}) {
                intensity.setFloat(model,pressure); hold.setInt(model,previousHold); max.invoke(model,(Object)null);
                if ((float)modelType.getMethod("getSuppressionIntensity").invoke(model)!=1.0F
                        || (int)modelType.getMethod("getSuppressionHoldTicks").invoke(model)!=300
                        || (int)modelType.getMethod("getAdrenalineLevel").invoke(model)!=5
                        || (int)modelType.getMethod("getAdrenalineTicks").invoke(model)!=200) {
                    throw new AssertionError("injection max suppression snapshot");
                }
            }
            Object nbt = modelType.getMethod("serializeNBT").invoke(model);
            Number synced;
            try { synced=(Number)nbt.getClass().getMethod("getFloatOr",String.class,float.class).invoke(nbt,"suppressionIntensity",0.0F); }
            catch (NoSuchMethodException e) { synced=(Number)nbt.getClass().getMethod("getFloat",String.class).invoke(nbt,"suppressionIntensity"); }
            if (synced.floatValue()!=1.0F) throw new AssertionError("max suppression sync payload");
            result.append("PASS native injection max-suppression helper and serialized sync snapshot: 100%, 300 hold ticks, level 5\n");
            Field cueDuration=modelType.getDeclaredField("tinnitusCueDurationTicks"); cueDuration.setAccessible(true);
            cueDuration.setInt(model,500); Object payload=modelType.getMethod("serializeNBT").invoke(model);
            Object restored=modelType.getConstructor().newInstance();
            Method deserialize=modelType.getMethod("deserializeNBT",payload.getClass()); deserialize.invoke(restored,payload);
            if ((int)modelType.getMethod("getTinnitusCueDurationTicks").invoke(restored)!=100) throw new AssertionError("head duration bound");
            payload.getClass().getMethod("remove",String.class).invoke(payload,"tinnitusCueDurationTicks"); deserialize.invoke(restored,payload);
            if ((int)modelType.getMethod("getTinnitusCueDurationTicks").invoke(restored)!=0) throw new AssertionError("old sync payload compatibility");
            result.append("PASS native head tinnitus metadata sync, duration bound and old payload compatibility\n");
            checkHeartbeat(loader,model,result);
        } finally { if (unload!=null) unload.invoke(spec,(Object)null); }

        Object feedback = loader.loadClass("ichttt.mods.firstaid.client.ClientEventHandler").getMethod("getSuppressionFeedbackController").invoke(null);
        Class<?> type=feedback.getClass();
        Field compression=type.getDeclaredField("sustainedFovCompression"), pain=type.getDeclaredField("painFovCompression");
        compression.setAccessible(true); pain.setAccessible(true);
        Method apply=Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("applyFov")).findFirst().orElseThrow();
        Method miss=type.getMethod("onNearMiss",loader.loadClass("net.minecraft.world.entity.player.Player"),float.class,float.class,float.class);
        boolean fabric=apply.getParameterTypes()[0]==float.class;
        Object event=null; Method get=null, set=null;
        if (!fabric) {
            Class<?> eventType=apply.getParameterTypes()[0];
            Constructor<?> ctor=Arrays.stream(eventType.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            Class<?>[] types=ctor.getParameterTypes(); Object[] args=new Object[types.length];
            for (int i=0;i<args.length;i++) {
                if (types[i]==float.class) args[i]=0.0F;
                else if (types[i]==double.class) args[i]=0.0D;
                else if (types[i]==boolean.class) args[i]=true;
                else if (types[i].getName().equals("net.minecraft.client.Camera")) args[i]=types[i].getConstructor().newInstance();
                else args[i]=client.getClass().getField("gameRenderer").get(client);
            }
            event=ctor.newInstance(args); get=eventType.getMethod("getFOV");
            set=Arrays.stream(eventType.getMethods()).filter(m -> m.getName().equals("setFOV")).findFirst().orElseThrow();
        }
        try {
            type.getMethod("clear").invoke(feedback);
            for (float value : new float[]{0,0.4F,1,3.2F}) for (float painValue : new float[]{0,4,12}) {
                compression.setFloat(feedback,value); pain.setFloat(feedback,painValue);
                for (int shots : new int[]{0,1000}) {
                    for (int i=0;i<shots;i++) miss.invoke(feedback,null,1.0F,1.0F,1.0F);
                    float actual;
                    if (fabric) actual=(float)apply.invoke(feedback,90.0F);
                    else {
                        Object base;
                        if (set.getParameterTypes()[0]==float.class) base=90.0F; else base=90.0D;
                        set.invoke(event,base); apply.invoke(feedback,event); actual=((Number)get.invoke(event)).floatValue();
                    }
                    if (Math.abs(actual-(90-(value*1.21F+painValue)))>0.00001F) throw new AssertionError("suppression +10% or near-miss FOV");
                }
            }
            result.append("PASS native suppression FOV another +10% (1.21x original), unchanged pain FOV, zero near-miss FOV after 1000 shots\n");
        } finally { type.getMethod("clear").invoke(feedback); }
    }

    private static void checkHeartbeat(ClassLoader loader, Object model, StringBuilder result) throws Exception {
        Class<?> type=loader.loadClass("ichttt.mods.firstaid.client.HeartbeatSoundController");
        Object controller=type.getConstructor().newInstance();
        Method suppressed=type.getDeclaredMethod("isSuppressed",model.getClass()); suppressed.setAccessible(true);
        Field intensity=model.getClass().getDeclaredField("suppressionIntensity"); intensity.setAccessible(true);
        for (float pressure : new float[]{0,0.049F,0.05F,Math.nextUp(0.05F),0.2F,1}) {
            intensity.setFloat(model,pressure);
            if ((boolean)suppressed.invoke(controller,model)!=(pressure>0.05F)) throw new AssertionError("heartbeat threshold");
        }
        Class<?> loop=loader.loadClass("ichttt.mods.firstaid.client.HeartbeatSoundController$HeartbeatLoopSound");
        Object holder=loader.loadClass("ichttt.mods.firstaid.common.RegistryObjects").getField("HEARTBEAT").get(null);
        Object event;
        try { event=holder.getClass().getMethod("get").invoke(holder); }
        catch (NoSuchMethodException e) { event=holder.getClass().getMethod("value").invoke(holder); }
        Constructor<?> ctor=loop.getDeclaredConstructors()[0]; ctor.setAccessible(true); Object sound=ctor.newInstance(null,event);
        Method alive=loop.getDeclaredMethod("keepAlive",float.class); alive.setAccessible(true);
        Method volume=loop.getDeclaredMethod("getVolume"), pitch=loop.getDeclaredMethod("getPitch"); volume.setAccessible(true); pitch.setAccessible(true);
        for (float strength : new float[]{0,0.1F,0.3F,0.65F,1}) {
            for (int i=0;i<200;i++) alive.invoke(sound,strength);
            if (Math.abs((float)volume.invoke(sound)-(0.4F+0.6F*strength))>0.000001F
                    || Math.abs((float)pitch.invoke(sound)-(0.95F+0.25F*strength))>0.000001F) throw new AssertionError("heartbeat volume/pitch curve");
        }
        result.append("PASS native heartbeat threshold 5%, smooth pressure-scaled volume 0.4-1.0 and speed 0.95-1.20\n");
    }

    private static Object decode(ClassLoader loader, String type, String json) throws Exception {
        Object codec=loader.loadClass(type).getField("CODEC").get(null);
        Class<?> codecType=loader.loadClass("com.mojang.serialization.Codec");
        if (!codecType.isInstance(codec)) codec=loader.loadClass("com.mojang.serialization.MapCodec").getMethod("codec").invoke(codec);
        Object input=loader.loadClass("com.google.gson.JsonParser").getMethod("parseString",String.class).invoke(null,json);
        Object ops=loader.loadClass("com.mojang.serialization.JsonOps").getField("INSTANCE").get(null);
        Object decoded=codecType.getMethod("parse",loader.loadClass("com.mojang.serialization.DynamicOps"),Object.class).invoke(codec,ops,input);
        return ((Optional<?>)loader.loadClass("com.mojang.serialization.DataResult").getMethod("result").invoke(decoded)).orElseThrow();
    }

    private static void checkInjuryData(ClassLoader loader, StringBuilder result) throws Exception {
        String path="data/firstaid/firstaid/debuffs/", prefix="ichttt.mods.firstaid.common.damagesystem.debuff.";
        Object legacy=decode(loader,prefix+"OnHitDebuffEntry","{\"damageTakenThreshold\":2,\"effectDuration\":40}");
        if ((float)legacy.getClass().getMethod("healthFractionThreshold").invoke(legacy)!=0.0F) throw new AssertionError("legacy debuff codec");
        for (String name : List.of("blindness_head","darkness_head","darkness_body","mining_fatigue_head","slowness_body")) {
            String json;
            try (var stream=loader.getResourceAsStream(path+name+".json")) { json=new String(stream.readAllBytes(),"UTF-8"); }
            Object builder=decode(loader,prefix+"builder.OnHitDebuffBuilder",json);
            Object debuff=builder.getClass().getMethod("build").invoke(builder);
            Field cooldown=debuff.getClass().getDeclaredField("cooldownTicks"); cooldown.setAccessible(true);
            if (cooldown.getInt(debuff)!=80) throw new AssertionError("per-part cooldown factory");
            if (name.startsWith("darkness_")) {
                Object effect=debuff.getClass().getField("effect").get(debuff);
                Object darkness=loader.loadClass("net.minecraft.world.effect.MobEffects").getField("DARKNESS").get(null);
                if (!effect.equals(darkness)) throw new AssertionError("wrong injury darkness effect");
                Field boundaries=debuff.getClass().getDeclaredField("timeBoundaries"); boundaries.setAccessible(true);
                Class<?> instanceType=loader.loadClass("net.minecraft.world.effect.MobEffectInstance");
                Constructor<?> ctor=Arrays.stream(instanceType.getConstructors()).filter(c -> c.getParameterCount()==5).findFirst().orElseThrow();
                Method down=instanceType.getDeclaredMethod("tickDownDuration"); down.setAccessible(true);
                Method getDuration=instanceType.getMethod("getDuration");
                Method update=loader.loadClass("ichttt.mods.firstaid.api.debuff.IDebuff").getMethod("update",
                        loader.loadClass("net.minecraft.world.entity.player.Player"),float.class);
                for (Object boundary : (List<?>)boundaries.get(debuff)) {
                    int duration=(int)boundary.getClass().getMethod("effectDuration").invoke(boundary);
                    if (duration!=40 && !(name.equals("darkness_head") && duration==50)) throw new AssertionError("darkness duration");
                    Object instance=ctor.newInstance(effect,duration,0,false,false);
                    for (int i=0;i<duration;i++) {
                        update.invoke(debuff,null,0.1F); // Low health alone cannot refresh on-hit darkness.
                        down.invoke(instance);
                        if ((int)getDuration.invoke(instance)!=duration-i-1) throw new AssertionError("darkness expiry");
                    }
                }
            }
        }
        if (loader.getResource(path+"nausea_head.json")!=null || loader.getResource(path+"nausea_body.json")!=null) throw new AssertionError("stale nausea resource");
        result.append("PASS native injury JSON codecs, legacy compatibility, head/body cooldown factories and no stale nausea definitions\n");
        result.append("PASS native head/torso darkness effect registry, 2-2.5 second expiry and no passive low-health refresh\n");
    }

    private static void checkCamera(ClassLoader loader, Object client, StringBuilder result) throws Exception {
        Class<?> handler = loader.loadClass("ichttt.mods.firstaid.client.ClientEventHandler");
        Object feedback = handler.getMethod("getSuppressionFeedbackController").invoke(null);
        Class<?> type = feedback.getClass();
        Field shake = type.getDeclaredField("shakeStrength"), impulse = type.getDeclaredField("yawImpulse"), verticalImpulse = type.getDeclaredField("pitchImpulse"), suppression = type.getDeclaredField("suppressionIntensity");
        shake.setAccessible(true); impulse.setAccessible(true); verticalImpulse.setAccessible(true); suppression.setAccessible(true);
        Method clear = type.getMethod("clear");
        Class<?> cameraType = loader.loadClass("net.minecraft.client.Camera");
        Object camera = cameraType.getConstructor().newInstance();
        Method setRotation = cameraType.getDeclaredMethod("setRotation", float.class, float.class);
        setRotation.setAccessible(true);
        Method setRotationWithRoll = null;
        try {
            setRotationWithRoll = cameraType.getDeclaredMethod("setRotation", float.class, float.class, float.class);
            setRotationWithRoll.setAccessible(true);
        } catch (NoSuchMethodException ignored) {} // Forge 1.20.1 applies event roll in GameRenderer.
        Field yaw = cameraType.getDeclaredField("yRot"), pitch = cameraType.getDeclaredField("xRot"), left = cameraType.getDeclaredField("left"), forwards = cameraType.getDeclaredField("forwards");
        yaw.setAccessible(true); pitch.setAccessible(true); left.setAccessible(true); forwards.setAccessible(true);
        Method hook = Arrays.stream(cameraType.getDeclaredMethods())
                .filter(m -> m.getName().contains("firstaid$cameraAngles")).findFirst().orElse(null);
        Object bus = null;
        Constructor<?> eventConstructor = null;
        Method post = null;
        boolean fabric = hook != null;
        Method argsFactory = null;
        Field partial = null;
        if (fabric) {
            hook.setAccessible(true);
            // Use Mixin's native argument container; loading Camera also validates required injection sites.
            for (int i=1;i<=64 && argsFactory==null;i++) {
                try { argsFactory=loader.loadClass("org.spongepowered.asm.synthetic.args.Args$"+i).getMethod("of",float.class,float.class); }
                catch (ClassNotFoundException | NoSuchMethodException ignored) {}
            }
            if (argsFactory==null) throw new AssertionError("no native camera arguments");
            if (hook.getParameterCount()==1) {
                partial=cameraType.getDeclaredField("partialTickTime"); partial.setAccessible(true);
            }
        } else {
            String owner;
            try { loader.loadClass("net.neoforged.neoforge.common.NeoForge"); owner="net.neoforged.neoforge"; }
            catch (ClassNotFoundException e) { owner="net.minecraftforge"; }
            Class<?> eventType = loader.loadClass(owner+".client.event.ViewportEvent$ComputeCameraAngles");
            eventConstructor = Arrays.stream(eventType.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            bus = loader.loadClass(owner+".common."+(owner.endsWith("neoforge") ? "NeoForge" : "MinecraftForge"))
                    .getField("EVENT_BUS").get(null);
            post = Arrays.stream(bus.getClass().getMethods()).filter(m -> m.getName().equals("post") && m.getParameterCount()==1
                    && m.getParameterTypes()[0].isAssignableFrom(eventType)).findFirst().orElseThrow();
        }
        try {
            clear.invoke(feedback);
            Method miss = type.getMethod("onNearMiss", loader.loadClass("net.minecraft.world.entity.player.Player"), float.class, float.class, float.class);
            for (float side : new float[]{-1,1}) {
                for (int i=0;i<1000;i++) miss.invoke(feedback,null,1.0F,side,side);
                if (Math.abs(impulse.getFloat(feedback)) > 0.22F || Math.abs(verticalImpulse.getFloat(feedback)) > 0.06F) {
                    throw new AssertionError("unbounded near-miss kick");
                }
                clear.invoke(feedback);
            }
            result.append("PASS native repeated near-miss limit\n");
            shake.setFloat(feedback,0.3F);
            float lastRoll=0;
            for (float pressure : new float[]{0,0.25F,1,2})
            for (float frequency : new float[]{0.7F,0.9F,2.24F}) for (int i=0;i<360;i++) {
                suppression.setFloat(feedback,pressure);
                float time=(float)(2*Math.PI*i/(frequency*360));
                float expectedYaw=12+0.3F*0.28F*(float)Math.sin(time*0.7F);
                float expectedPitch=-8+0.3F*0.07F*(float)Math.cos(time*0.9F);
                float expectedRoll=Math.min(pressure,1)*1.6F*(float)Math.sin(time*2.24F);
                lastRoll=expectedRoll;
                if (fabric) {
                    Object args=argsFactory.invoke(null,12.0F,-8.0F);
                    if (partial!=null) { partial.setFloat(camera,time); hook.invoke(camera,args); }
                    else hook.invoke(camera,args,time);
                    Method get=args.getClass().getMethod("get",int.class);
                    setRotation.invoke(camera,get.invoke(args,0),get.invoke(args,1));
                } else {
                    setRotation.invoke(camera,12.0F,-8.0F);
                    Class<?>[] types=eventConstructor.getParameterTypes(); Object[] args=new Object[types.length];
                    float[] angles={12,-8,0};
                    for (int j=0;j<args.length;j++) {
                        if (j>=args.length-3) args[j]=angles[j-(args.length-3)];
                        else if (types[j]==double.class) args[j]=(double)time;
                        else if (types[j]==float.class) args[j]=time;
                        else if (types[j]==cameraType) args[j]=camera;
                        else args[j]=client.getClass().getField("gameRenderer").get(client);
                    }
                    Object event=eventConstructor.newInstance(args);
                    if ((float)event.getClass().getMethod("getYaw").invoke(event)!=12 || (float)event.getClass().getMethod("getPitch").invoke(event)!=-8) {
                        throw new AssertionError("camera event baseline: "+eventConstructor);
                    }
                    post.invoke(bus,event);
                    float roll=(float)event.getClass().getMethod("getRoll").invoke(event);
                    if (Math.abs(roll-expectedRoll)>0.000002F) throw new AssertionError("roll does not scale with suppression");
                    Object eventYaw=event.getClass().getMethod("getYaw").invoke(event), eventPitch=event.getClass().getMethod("getPitch").invoke(event);
                    if (setRotationWithRoll!=null) setRotationWithRoll.invoke(camera,eventYaw,eventPitch,roll);
                    else setRotation.invoke(camera,eventYaw,eventPitch);
                }
                if (Math.abs(yaw.getFloat(camera)-expectedYaw)>0.000002F || Math.abs(pitch.getFloat(camera)-expectedPitch)>0.000002F) {
                    throw new AssertionError("camera hook differs at phase "+i+": yaw="+yaw.getFloat(camera)+" expected="+expectedYaw
                            +", pitch="+pitch.getFloat(camera)+" expected="+expectedPitch+", shake="+shake.getFloat(feedback));
                }
                Object vector=left.get(camera);
                double expectedLeftY=(fabric || setRotationWithRoll!=null)
                        ? Math.cos(Math.toRadians(expectedPitch))*Math.sin(Math.toRadians(expectedRoll)) : 0;
                if (Math.abs(vector.getClass().getField("y").getFloat(vector)-expectedLeftY)>0.000002F) {
                    throw new AssertionError("roll orientation differs from expected centered sway");
                }
                // View-axis roll must leave the forward/aim direction intact.
                Object forward=forwards.get(camera);
                double[] expectedForward={-Math.sin(Math.toRadians(expectedYaw))*Math.cos(Math.toRadians(expectedPitch)),
                        -Math.sin(Math.toRadians(expectedPitch)),Math.cos(Math.toRadians(expectedYaw))*Math.cos(Math.toRadians(expectedPitch))};
                String[] components={"x","y","z"};
                for (int j=0;j<3;j++) if (Math.abs(forward.getClass().getField(components[j]).getFloat(forward)-expectedForward[j])>0.000002F) {
                    throw new AssertionError("roll changed facing direction");
                }
            }
            result.append("PASS native camera hook: 4320 phases, suppression-scaled 7.13-Hz roll capped at 1.6 degrees, subtle vertical motion, unchanged roll aim\n");
            float beforeYaw=yaw.getFloat(camera), beforePitch=pitch.getFloat(camera);
            if (fabric) {
                Method mirror=Arrays.stream(cameraType.getDeclaredMethods()).filter(m -> m.getName().contains("firstaid$mirrorRoll")).findFirst().orElseThrow();
                mirror.setAccessible(true); mirror.invoke(camera,(Object)null);
            }
            if (setRotationWithRoll!=null) setRotationWithRoll.invoke(camera,beforeYaw+180,-beforePitch,-lastRoll);
            else setRotation.invoke(camera,beforeYaw+180,-beforePitch);
            if (yaw.getFloat(camera)!=beforeYaw+180 || pitch.getFloat(camera)!=-beforePitch) throw new AssertionError("feedback applied twice on mirror rotation");
            Object mirrorLeft=left.get(camera);
            double mirrorY=(fabric || setRotationWithRoll!=null) ? Math.cos(Math.toRadians(beforePitch))*Math.sin(Math.toRadians(-lastRoll)) : 0;
            if (Math.abs(mirrorLeft.getClass().getField("y").getFloat(mirrorLeft)-mirrorY)>0.000002F) throw new AssertionError("mirror roll did not invert");
            if (fabric) {
                Method sleeping=Arrays.stream(cameraType.getDeclaredMethods()).filter(m -> m.getName().contains("firstaid$sleepingRoll")).findFirst().orElseThrow();
                sleeping.setAccessible(true); sleeping.invoke(camera,(Object)null);
            }
            setRotation.invoke(camera,90.0F,0.0F);
            Object sleepingLeft=left.get(camera);
            if (Math.abs(sleepingLeft.getClass().getField("y").getFloat(sleepingLeft))>0.000002F) throw new AssertionError("sleeping roll left behind");
            result.append("PASS native mirror/sleeping rotations keep roll consistent without repeated feedback\n");
        } finally { clear.invoke(feedback); }
    }

    private static void checkCommands(ClassLoader loader, Object client, StringBuilder result) throws Exception {
        Class<?> dispatcherType = loader.loadClass("com.mojang.brigadier.CommandDispatcher");
        Object dispatcher = dispatcherType.getConstructor().newInstance();
        loader.loadClass("ichttt.mods.firstaid.client.TinnitusCommands").getMethod("register", dispatcherType).invoke(null, dispatcher);
        Object source;
        try {
            Class<?> fabricSource = loader.loadClass("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource");
            source = Proxy.newProxyInstance(loader, new Class<?>[]{fabricSource}, (proxy, method, args) -> {
                if (method.getName().equals("getClient")) return client;
                if (method.getReturnType() == boolean.class) return false;
                return null;
            });
        } catch (ClassNotFoundException e) {
            Class<?> nativeSource = loader.loadClass("net.minecraft.commands.CommandSourceStack");
            Constructor<?> ctor = Arrays.stream(nativeSource.getConstructors())
                    .filter(c -> Arrays.stream(c.getParameterTypes()).anyMatch(t -> t.getName().equals("net.minecraft.network.chat.Component")))
                    .findFirst().orElseThrow();
            Object[] args = new Object[ctor.getParameterCount()];
            Class<?>[] types = ctor.getParameterTypes();
            for (int i=0; i<args.length; i++) {
                String name = types[i].getName();
                if (types[i] == int.class) args[i]=0;
                else if (types[i] == String.class) args[i]="FirstAidProbe";
                else if (name.endsWith(".Vec3") || name.endsWith(".Vec2")) args[i]=types[i].getField("ZERO").get(null);
                else if (name.endsWith(".CommandSource")) args[i]=types[i].getField("NULL").get(null);
                else if (name.endsWith(".PermissionSet")) args[i]=types[i].getField("NO_PERMISSIONS").get(null);
                else if (name.endsWith(".Component")) args[i]=types[i].getMethod("literal", String.class).invoke(null, "FirstAidProbe");
            }
            source=ctor.newInstance(args);
        }
        Object config = loader.loadClass("ichttt.mods.firstaid.FirstAidConfig").getField("CLIENT").get(null);
        Object enabled = config.getClass().getField("tinnitusEnabled").get(config);
        Object percent = config.getClass().getField("tinnitusDurationPercent").get(config);
        Object beforeEnabled = enabled.getClass().getMethod("get").invoke(enabled);
        Object beforePercent = percent.getClass().getMethod("get").invoke(percent);
        Method execute = dispatcherType.getMethod("execute", String.class, Object.class);
        try {
            for (String command : List.of("firstaid tinnitus", "firstaid tinnitus off", "firstaid tinnitus on",
                    "firstaid tinnitus duration 1", "firstaid tinnitus duration 75", "firstaid tinnitus duration 400")) {
                if ((int) execute.invoke(dispatcher, command, source) != 1) throw new AssertionError(command);
                result.append("PASS no-permission command ").append(command).append('\n');
            }
            for (int invalid : new int[]{0,401}) {
                try { execute.invoke(dispatcher,"firstaid tinnitus duration "+invalid,source); throw new AssertionError("invalid command accepted"); }
                catch (InvocationTargetException expected) {
                    if (!expected.getCause().getClass().getName().endsWith("CommandSyntaxException")) throw expected;
                }
            }
            if ((int) percent.getClass().getMethod("get").invoke(percent) != 400) throw new AssertionError("command value");
            if (source.getClass().getInterfaces().length > 0
                    && source.getClass().getInterfaces()[0].getName().contains("FabricClientCommandSource")) {
                try {
                    execute.invoke(dispatcher, "firstaid pain dynamic", source);
                    throw new AssertionError("Fabric server command was consumed by the client dispatcher");
                } catch (InvocationTargetException expected) {
                    Throwable syntax = expected.getCause();
                    Object actualType = syntax.getClass().getMethod("getType").invoke(syntax);
                    Object builtins = syntax.getClass().getField("BUILT_IN_EXCEPTIONS").get(null);
                    Object unknownCommand = builtins.getClass().getMethod("dispatcherUnknownCommand").invoke(builtins);
                    if (actualType != unknownCommand) throw expected;
                }
                result.append("PASS Fabric client root forwards other /firstaid branches to the server\n");
            }
            java.io.File gameDirectory = (java.io.File) client.getClass().getField("gameDirectory").get(client);
            Path json = gameDirectory.toPath().resolve("config/firstaid/firstaid-client.json");
            Path configFile = Files.exists(json) ? json : gameDirectory.toPath().resolve("config/firstaid-client.toml");
            String persisted = Files.readString(configFile);
            if (!java.util.regex.Pattern.compile("tinnitusDurationPercent\\\"?\\s*[:=]\\s*400").matcher(persisted).find()) {
                throw new AssertionError("command setting not persisted: " + configFile);
            }
        } finally {
            enabled.getClass().getMethod("set", Object.class).invoke(enabled,beforeEnabled);
            percent.getClass().getMethod("set", Object.class).invoke(percent,beforePercent);
            loader.loadClass("ichttt.mods.firstaid.FirstAidConfig").getMethod("saveClient").invoke(null);
        }
        result.append("PASS command boundaries and client config persistence\n");
    }

    private static String stack(Throwable error) {
        java.io.StringWriter text = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }
}
