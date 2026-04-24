# First Aid Extension API

This document covers the public extension surface for third-party medicine items.

Only depend on `ichttt.mods.firstaid.api.*`.
Do not take a hard dependency on `ichttt.mods.firstaid.common.*`.

## Two extension paths

`ItemHealing`
- Use this for body-part treatment items such as bandages, splints, or custom surgical kits.
- Runtime flow stays two-stage: select a body part in the First Aid screen, then keep right click held until the server applies the treatment.

`ItemMedicine`
- Use this for direct-use medicine such as pills, syringes, stimulants, or suppressants.
- Runtime flow is standard item use: hold use until `finishUsingItem`, then your custom effect runs.

## Public types

`ichttt.mods.firstaid.api.healing.ItemHealing`
- Existing base class for part-healing items.
- New hooks:
`onTreatmentStarted(PartHealingContext context)`
`onHealPulse(PartHealingContext context)`
`onTreatmentCompleted(PartHealingContext context)`

`ichttt.mods.firstaid.api.healing.PartHealingContext`
- Exposes `Player`, `Level`, `ItemStack`, `AbstractPlayerDamageModel`, `AbstractDamageablePart`, and `AbstractPartHealer`.

`ichttt.mods.firstaid.api.medicine.ItemMedicine`
- Base class for direct-use medicines.
- Required override:
`applyMedicine(MedicineUseContext context)`
- Optional overrides:
`getUseDuration(...)`
`getUseAnimation(...)`
`getUseStartSound(...)`
`getUseLoopSound(...)`
`getUseFinishSound(...)`
`consumeAfterUse(...)`
`getActiveStatus(MedicineStatusContext context)`
`onUseCancelled(...)`

`ichttt.mods.firstaid.api.medicine.MedicineUseContext`
- Exposes `Player`, `Level`, `ItemStack`, and nullable `AbstractPlayerDamageModel`.
- First Aid helpers:
`queuePainkillerActivation()`
`queueMorphineActivation()`
`applyAdrenalineInjection()`

`ichttt.mods.firstaid.api.medicine.MedicineStatusContext`
- Exposes `Player`, `Level`, and nullable `AbstractPlayerDamageModel`.

`ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay`
- Fields:
`statusId`
`text`
`iconTexture`
`color`
- `statusId` is used for deduplication in the HUD and health screen.
- `iconTexture` may point to your own mod namespace.

## Lifecycle

### Part treatment

1. Player right-clicks an `ItemHealing`.
2. First Aid opens the body-part selector.
3. Player selects a part.
4. Player keeps right click held with the same item.
5. Server validates the request and starts treatment.
6. `onTreatmentStarted(...)` fires once.
7. Each real heal pulse calls `onHealPulse(...)`.
8. Before the healer ends, `onTreatmentCompleted(...)` fires once.

Closing the GUI alone does not clear the pending selection.
Switching away from the selected item does clear it.

### Direct medicine

1. Player starts using an `ItemMedicine`.
2. Optional start sound plays once.
3. Optional loop sound keeps playing while the item is in use.
4. `finishUsingItem(...)` calls `applyMedicine(...)`.
5. `consumeAfterUse(...)` runs after the effect.
6. Optional finish sound plays when use completes.
7. If use is released early, `onUseCancelled(...)` runs and the loop sound stops.

## Status text and icon rules

- Return `null` from `getActiveStatus(...)` when the medicine has no active state to show.
- Return a `MedicineStatusDisplay` when you want a HUD line and health-screen line.
- Use a stable `statusId` per logical effect, for example `mymod:tranquilizer`.
- If two items return the same `statusId`, First Aid renders only one line.
- Recommended HUD icon path:
`assets/<modid>/textures/gui/firstaid/<status>.png`
- Recommended item texture path:
`assets/<modid>/textures/item/<item_name>.png`

## Minimal `ItemHealing` example

```java
package com.example.mymod.item;

import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.common.damagesystem.PartHealer;
import net.minecraft.world.item.ItemStack;

public class TraumaPadItem extends ItemHealing {
    public TraumaPadItem(Properties properties) {
        super(properties.stacksTo(8));
    }

    @Override
    public PartHealer createNewHealer(ItemStack stack) {
        return new PartHealer(stack, 6, 20, true);
    }

    @Override
    public void onTreatmentStarted(PartHealingContext context) {
        context.getPlayer().removeAllEffects();
    }

    @Override
    public void onTreatmentCompleted(PartHealingContext context) {
        context.getPlayer().setRemainingFireTicks(0);
    }
}
```

## Minimal `ItemMedicine` example

```java
package com.example.mymod.item;

import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.api.medicine.MedicineUseContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class CombatStimItem extends ItemMedicine {
    private static final ResourceLocation STATUS_ID = ResourceLocation.fromNamespaceAndPath("mymod", "combat_stim");
    private static final ResourceLocation STATUS_ICON = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/firstaid/combat_stim.png");

    public CombatStimItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void applyMedicine(MedicineUseContext context) {
        context.getPlayer().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 1));
        context.getPlayer().addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 30, 0));
    }

    @Override
    public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
        return context.getPlayer().hasEffect(MobEffects.MOVEMENT_SPEED)
            ? new MedicineStatusDisplay(STATUS_ID, Component.literal("Combat stim active"), STATUS_ICON, 0xB7D96B)
            : null;
    }
}
```

## Sound resources

Put sound assets under your own namespace.

`assets/<modid>/sounds.json`

```json
{
  "item.combat_stim_start": {
    "sounds": ["mymod:item/combat_stim_start"]
  },
  "item.combat_stim_loop": {
    "sounds": ["mymod:item/combat_stim_loop"]
  },
  "item.combat_stim_finish": {
    "sounds": ["mymod:item/combat_stim_finish"]
  }
}
```

Then return the corresponding `SoundEvent` from the medicine hooks.

## Recommended verification

1. Confirm `ItemHealing` still requires body-part selection before use starts.
2. Confirm switching away from the selected item clears pending treatment.
3. Confirm closing the GUI alone does not clear pending treatment.
4. Confirm `ItemMedicine` start, loop, finish, and cancel behavior matches your overrides.
5. Confirm `getActiveStatus(...)` appears only while the effect is active.
6. Confirm shared `statusId` values do not render duplicate lines.

## Loader-specific setup

- Fabric: [firstaid-extension-fabric.md](./firstaid-extension-fabric.md)
- NeoForge: [firstaid-extension-neoforge.md](./firstaid-extension-neoforge.md)
- Forge 1.20.1: [firstaid-extension-forge1.20.1.md](./firstaid-extension-forge1.20.1.md)
