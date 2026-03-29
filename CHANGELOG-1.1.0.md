# Changelog

## 1.1.0 — 2026-03-29

- Added 7-second Regeneration I effect (140 ticks) to the adrenaline injector across `fabric1.21.1`, `fabric1.21.11`, `neoforge1.21.1`, and `neoforge1.21.11`. The effect applies in both the normal damage-model path and the fallback path.

## 1.0.0

### 2026-03-27

- Made downed players die immediately once all critical body parts are fully depleted across `fabric1.21.1`, `fabric1.21.11`, `neoforge1.21.1`, and `neoforge1.21.11`.
- Added a 90% damage reduction for critical body parts while a player is in the critical downed state, and applied the same rule to random, equal, and direct part damage distribution paths.
- Aligned Fabric 1.21.11 and NeoForge 1.21.11 critical death checks with the stricter multi-critical collapse handling already used in the other templates.

### 2026-03-26

- Aligned downed and rescue state handling across `fabric1.21.1`, `fabric1.21.11`, `neoforge1.21.1`, and `neoforge1.21.11`.
- Added runtime-detected `playerrevive` compatibility through reflection against `PlayerReviveServer#getBleeding(...)` and `IBleeding`, with safe fallback to vanilla death handling when the API is unavailable.
- Added `externalRevivePending` tracking so FirstAid waits for external revive completion and only restores its own recovery state after `playerrevive` finishes bleeding/revival.
- Updated shared kill handoff logic so external revive ownership is recorded whenever a fatal hit is intercepted by `playerrevive`.
- Brought NeoForge client sync recovery in line with Fabric by retrying local damage-model refreshes after login and when local sync targets are not ready.
- Aligned Fabric 1.21.11 unconscious player pose rendering with NeoForge, including body/jacket rotations and vertical collapse offset.
