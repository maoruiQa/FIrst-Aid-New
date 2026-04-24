# First Aid Extension API on NeoForge

Common API behavior is documented in [firstaid-extension-api.md](./firstaid-extension-api.md).

## Gradle

Use the matching NeoForge First Aid jar for your targeted Minecraft version.

```gradle
dependencies {
    compileOnly files("../libs/firstaid-neoforge-<mc-version>.jar")
    runtimeOnly files("../libs/firstaid-neoforge-<mc-version>.jar")
}
```

## Registration

Register items and sounds from your mod constructor or deferred-register setup.

```java
public final class MyMod {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mymod");
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, "mymod");

    public static final DeferredItem<Item> TRAUMA_PAD = ITEMS.register("trauma_pad", () -> new TraumaPadItem(new Item.Properties()));
    public static final DeferredItem<Item> COMBAT_STIM = ITEMS.register("combat_stim", () -> new CombatStimItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_STIM_START =
        SOUNDS.register("item.combat_stim_start", () -> SoundEvent.createVariableRangeEvent(id("item.combat_stim_start")));
}
```

## Minimal body-part item

```java
new TraumaPadItem(new Item.Properties())
```

## Minimal direct-use item

```java
new CombatStimItem(new Item.Properties().stacksTo(16))
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

```powershell
cd neoforge1.21.11
.\gradlew.bat build
.\gradlew.bat runClient
```

Check that `ItemHealing` still uses body-part selection first, and that `ItemMedicine` loop sounds stop immediately when use is cancelled.
