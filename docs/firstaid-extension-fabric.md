# First Aid Extension API on Fabric

Common API behavior is documented in [firstaid-extension-api.md](./firstaid-extension-api.md).

## Gradle

Use the matching Fabric First Aid jar for your targeted Minecraft version.

```gradle
dependencies {
    modCompileOnly files("../libs/firstaid-fabric-<mc-version>.jar")
    modLocalRuntime files("../libs/firstaid-fabric-<mc-version>.jar")
}
```

## Registration

Register your items and optional sounds from your Fabric initializer.

```java
public final class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, id("trauma_pad"), TRAUMA_PAD);
        Registry.register(BuiltInRegistries.ITEM, id("combat_stim"), COMBAT_STIM);
        Registry.register(BuiltInRegistries.SOUND_EVENT, id("item.combat_stim_start"), SoundEvent.createVariableRangeEvent(id("item.combat_stim_start")));
    }
}
```

## Minimal body-part item

```java
public static final Item TRAUMA_PAD = new TraumaPadItem(new Item.Properties());
```

## Minimal direct-use item

```java
public static final Item COMBAT_STIM = new CombatStimItem(new Item.Properties().stacksTo(16));
```

## Resource placement

`assets/<modid>/textures/item/trauma_pad.png`
`assets/<modid>/textures/item/combat_stim.png`
`assets/<modid>/textures/gui/firstaid/combat_stim.png`
`assets/<modid>/sounds.json`
`assets/<modid>/sounds/item/combat_stim_start.ogg`
`assets/<modid>/sounds/item/combat_stim_loop.ogg`
`assets/<modid>/sounds/item/combat_stim_finish.ogg`

## Verification

Run the build from the Fabric module you target.

```powershell
cd fabric1.21.11
.\gradlew.bat build
```

For local runtime checks, launch the matching Fabric dev client and verify the two-stage treatment flow plus direct medicine sounds/status text.
