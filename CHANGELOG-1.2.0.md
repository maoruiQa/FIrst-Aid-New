# Changelog

## 1.2.0 - 2026-04-25

Applies to:

- `fabric1.21.1`
- `fabric1.21.11`
- `fabric26.1`
- `neoforge1.21.1`
- `neoforge1.21.11`
- `neoforge26.1`
- `forge1.20.1`

### Highlights

- Added a public extension API for third-party treatment items and direct-use medicines, including shared use contexts, optional status display hooks, and loader-specific integration docs.
- Reworked direct-use medicines so they follow the same long-press feedback style as body-part treatment, with hold progress, action-bar guidance, and release-to-reuse gating.
- Retuned built-in medicine durations across all templates, including longer painkiller coverage and a stronger morphine suppression window.

### Added

- Added `ItemMedicine`, `MedicineUseContext`, `MedicineStatusContext`, and `MedicineStatusDisplay` to the public API for third-party medicine items.
- Added `PartHealingContext` plus treatment lifecycle hooks on `ItemHealing` so external healing items can attach effects at treatment start, heal pulses, and completion.
- Added extension API overview and loader-specific setup documentation for Fabric, NeoForge, and Forge 1.20.1.
- Added direct-use medicine progress prompts and action-bar feedback so pills and injections now expose clearer use timing in the HUD.

### Changed

- Changed built-in `painkillers`, `morphine`, and `adrenaline_injector` to use the shared medicine API instead of private hardcoded paths.
- Changed direct-use medicines so finishing one use now requires releasing and pressing use again before starting the next use.
- Changed medicine handling so custom third-party direct-use medicines inherit the same reuse gating, progress prompts, sound lifecycle, and optional status display behavior.
- Changed painkillers to last `4:00`.
- Changed morphine to last `7:30-8:30`.

### Fixed

- Fixed direct-use medicines incorrectly triggering eating-style animation and sound playback.
- Fixed client medicine prompts, hold state, and loop-sound cleanup so canceling or switching away from a medicine stops the active feedback correctly.
- Fixed durability-based medicine and rescue tools so uses once again consume durability as intended.
- Fixed the H-key health screen treatment flow so direct-use medicines and body-part treatment items follow clearer, more consistent interaction rules.

### Notes

- `1.2.0` is the first release in this repo that exposes the third-party medicine/treatment extension surface as a documented public API.
- All seven module builds pass with the `1.2.0` version bump.
