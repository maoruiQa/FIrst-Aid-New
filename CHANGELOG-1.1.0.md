# Changelog

## 1.1.0 - 2026-03-29

Applies to:

- `fabric1.21.1`
- `fabric1.21.11`
- `neoforge1.21.1`
- `neoforge1.21.11`

### Highlights

- Reworked the unconscious, rescue, and execution loop with timed interactions, contextual HUD prompts, configurable wake-up behavior, and server-authoritative handling.
- Reworked locational combat with exact projectile and directional melee part resolution, improved overflow routing and attenuation, and stricter critical collapse and death rules.
- Added the `Adrenaline Injector` together with a shared suppression and heartbeat feedback loop across all maintained templates.

### Added

- Added timed rescue with `bandage`, `plaster`, and `defibrillator`, plus timed execution with valid weapons and tools.
- Added rescue and execution prompts, progress bars, completion feedback, and updated English and Chinese localization for the new systems.
- Added `/firstaid revivewakeup on [seconds]|off`, persistent server-side wake-up settings, and clearer join-time admin command tips.
- Added the `Adrenaline Injector` item, recipe, automatic recipe unlocking, dedicated use sound, HUD and tooltip status, held-use behavior, and first-person injector rendering.
- Added suppression HUD text, suppression command tips, and heartbeat audio support for critical injuries, suppression pressure, and adrenaline-triggered pulses.
- Added `playerrevive` compatibility with safe fallback behavior when the external API is unavailable.

### Changed

- Changed rescue from an instant interaction into a timed action, and changed nearby downed-player prompts to switch between rescue and execution based on the held item.
- Changed wake-up handling so `revivewakeup` defaults to `off`, uses `on [seconds]` or `off`, defaults to a 20-second delay when enabled, reduces normal rescue recovery to 30% of the configured delay, and reduces defibrillator recovery to 40%.
- Changed immediate rescue without wake-up delay so zero-health non-critical parts recover to 1 health and critical parts recover to 2 health.
- Changed defibrillator durability from 5 to 3.
- Changed body-part health colors, visible full-health handling, treatment availability, and pain display so the HUD and health UI follow the same rounded presentation.
- Changed creative-mode medical item use so the effect still applies without consuming the held stack.
- Changed downed-player damage handling to give 80% damage reduction, with an additional 90% reduction on critical body parts during the critical downed state.
- Changed critical collapse so players die as soon as all critical body parts are depleted, and aligned that stricter logic across all maintained templates.
- Changed adrenaline from temporary direct healing to a full effect package: extended `Absorption`, `Painkiller`, `Haste I`, `Strength I`, `Speed I`, a 35-second main window, and a 7-second `Regeneration I` burst.
- Changed adrenaline so it also applies light suppression and pain-suppression feedback for the adrenaline window.
- Changed heartbeat playback to a single managed loop with stable fade-out behavior instead of stacked overlapping beats.

### Fixed

- Fixed cases where downed players could appear standing or otherwise desynced for other players.
- Fixed damage-model synchronization for unconscious, rescue, and tracked-player state updates, including local client recovery after login or late sync.
- Fixed rescue progress stalls and added a more reliable rescue completion fallback path.
- Fixed rescue wake-up timing so rescued players no longer keep stale critical unconscious timers, and fixed the same timing path for `playerrevive` revivals.
- Fixed cases where critical collapse could bypass the intended unconscious flow or behave inconsistently across templates.
- Fixed invalid overflow paths, cross-version damage-distribution parity issues, and missing knockback or hurt feedback.
- Fixed cases where a part could look fully healed while still contributing pain or accepting extra treatment.
- Fixed Fabric healing-item snapshot handling so active treatment no longer mutates the live held stack reference.
- Fixed heartbeat stacking and adrenaline pulse duplication so heartbeat audio stays consistent.

### Notes

- Rescue remains server-authoritative.
- Maintained templates now target near-identical gameplay behavior across Fabric and NeoForge.
- All four maintained templates build successfully after these changes.
