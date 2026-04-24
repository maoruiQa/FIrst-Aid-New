# First Aid Extension API on Forge 1.20.1

Common API behavior is documented in [firstaid-extension-api.md](./firstaid-extension-api.md).

## Gradle

Use the Forge 1.20.1 First Aid jar that matches your dev environment.

```gradle
dependencies {
    compileOnly files("../libs/firstaid-forge-1.20.1.jar")
    runtimeOnly files("../libs/firstaid-forge-1.20.1.jar")
}
```

## Registration

Use normal Forge deferred registration.

```java
public final class MyMod {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "mymod");
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "mymod");

    public static final RegistryObject<Item> TRAUMA_PAD = ITEMS.register("trauma_pad", () -> new TraumaPadItem(new Item.Properties()));
    public static final RegistryObject<Item> COMBAT_STIM = ITEMS.register("combat_stim", () -> new CombatStimItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<SoundEvent> COMBAT_STIM_START =
        SOUNDS.register("item.combat_stim_start", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("mymod", "item.combat_stim_start")));
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
cd forge1.20.1
.\gradlew.bat build
.\gradlew.bat runClient
```

Confirm direct medicines apply their custom effects on finish, and confirm status text/icons disappear automatically once the effect ends.
