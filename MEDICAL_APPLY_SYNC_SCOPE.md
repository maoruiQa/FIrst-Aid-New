# Medical Apply Sync Scope

This document records the full behavior delta already implemented in `fabric26.1` and intended to be ported to the other branches.

## Target branches

- `fabric1.21.1`
- `fabric1.21.11`
- `neoforge1.21.1`
- `neoforge1.21.11`
- `neoforge26.1`
- `forge1.20.1`

## Feature scope

### 1. Two-step healing flow for body-part healing items

- First right click only opens the body-part selection UI.
- Selecting a body part no longer applies the item immediately.
- After selection, the player enters a client-side pending-healing state.
- The player must keep holding right click with the same hand and same item until `ItemHealing#getApplyTime(stack)` is completed.
- Only after the hold finishes does the client send `MessageApplyHealingItem`.
- The server remains the final authority for consumption and healer attachment.

### 2. Pending-healing state rules

- State includes selected `EnumPlayerPart`, selected `InteractionHand`, selected item identity, hold duration, current progress, and trigger state.
- Releasing right click cancels progress but keeps the selection if the item never changed.
- Switching to another item clears the selection immediately.
- Switching back to the original item does not restore the selection.
- Opening unrelated screens, death, unconsciousness, disconnect, or other invalidation conditions clear the pending state.
- After a successful application, the player must release right click before a new selection UI can open again.

### 3. UI and prompt behavior

- `GuiHealthScreen` click behavior changes from immediate application to pending selection.
- The bottom hint text explains that the player must keep right click held after choosing a body part.
- The action bar shows a live countdown while a healing selection is pending.
- `StatusEffectLayer` reuses the existing centered prompt/progress style for self-healing.
- Self-healing prompt has higher priority than rescue/execute prompts.
- If no body part is currently treatable, right click must not open the selection UI at all.

### 4. Item use behavior

- Healing items that require body-part selection now enter a normal held-use state during the second step, similar to the adrenaline injector behavior.
- Once healing finishes, continuing to hold right click does not reopen the menu and does not keep the hand swinging.
- A fresh release-and-press cycle is required before opening the selection UI again.

### 5. Healing sound behavior

- `ItemHealing` now exposes per-item application sound configuration.
- Supported strategies:
  - play while using, stop when right click is released or canceled
  - play only on successful completion
- Existing bandage and plaster are configured to play while using, and stop when use stops.

### 6. Server-side validation

- `MessageApplyHealingItem` revalidates:
  - current hand still holds an `ItemHealing`
  - stack is still present and count is sufficient
  - healer can still be created
  - target part is still treatable
- Item shrink and `activeHealer` assignment only happen after these checks succeed.

### 7. Localization

- New healing prompt/action-bar language keys added in both `en_us` and `zh_cn`.
- Existing apply hint text updated to describe the two-step flow.

### 8. Timing default

- Bandage default `applyTime` is now `3000` ms.

## Main code areas affected

- `api/healing/ItemHealing`
- `common/apiimpl/HealingItemApiHelperImpl`
- `common/ClientAccess` where present
- `client/ClientHooks`
- `client/ClientEventHandler`
- `client/StatusEffectLayer`
- `client/HealingSoundController`
- `client/gui/GuiHealthScreen`
- `common/network/MessageApplyHealingItem`
- branch-specific config/registry/lang files
