# First Aid New

First Aid New is a multi-loader port of ichttt’s classic **First Aid**, rebuilt for modern Minecraft. It replaces the single vanilla health bar with per-body-part damage, injury debuffs, timed medicine, unconsciousness and rescue, and a full client feedback layer—pain, adrenaline, heartbeat audio, and HUD overlays that make survival feel physical again.

Original project: [First Aid on CurseForge](https://www.curseforge.com/minecraft/mc-mods/first-aid)

---

## 1.3.5

**1.3.5** lets server administrators choose what happens when a player's head or body reaches 0 HP, across all seven maintained builds. Players are shown the current rules in a full-screen notice the first time they join, and again whenever the rules change.

### What's new

- **Knockdown mode:** `always` downs the player every time (the previous behaviour), `chance` (default) lets the overflow damage decide, and `never` skips the downed stage and kills immediately. Losing the head and body together stays fatal, totems still work, and players who are already down are not affected by a change.
- **Overflow-based chance:** In `chance` mode the downed chance is `exp(-k × overflow / part max HP)`. The default `k = 0.35` favours going down: an exact kill always downs, one part-max of overflow downs 70% of the time, two 50%, five 17%. Admins can set `k` from 0 (always down) to 5.
- **Commands and config:** `/firstaid knockdown [always|chance|never]` and `/firstaid knockdown coefficient <0-5>` (operator only) save immediately; the same settings are the `knockdownMode` and `knockdownOverflowCoefficient` server config keys, applied on the next start when edited by hand.
- **Knockdown notice:** A full-screen notice explains the rules on first join and after every change: right away for online players, on the next join for everyone else. It must be confirmed with its button; acknowledgements are stored per world.

### Supported builds (1.3.5)

| Artifact | Loader | Minecraft |
|----------|--------|-----------|
| `firstaid-1.3.5+forge1.20.1` | Forge | 1.20.1 |
| `firstaid-1.3.5+fabric1.21.1` | Fabric | 1.21.1 |
| `firstaid-1.3.5+neoforge1.21.1` | NeoForge | 1.21.1 |
| `firstaid-1.3.5+fabric26.2` | Fabric | 26.2 |
| `firstaid-1.3.5+neoforge26.2` | NeoForge | 26.2 |
| `firstaid-1.3.5+fabric26.3` | Fabric | 26.3 |
| `firstaid-1.3.5+neoforge26.3` | NeoForge | 26.3 |

The 1.21.11 and 26.1 modules are legacy-only and no longer supported. Forge's network protocol is now `7.1`; use matching First Aid versions on the client and server.

Full notes: [1.3.5](./1.3.5changelog.md) · [1.3.4](./1.3.4changelog.md) · [1.3.3](./1.3.3changelog.md) · [1.3.2](./1.3.2changelog.md) · [1.3.1](./1.3.1changelog.md)

---

## Features

- **Locational health** — head, body, arms, legs, feet; each with its own pool and overflow rules  
- **Injury debuffs** — limb damage that changes how you move, dig, and fight  
- **Medicine with pacing** — bandages, plaster, painkillers, morphine, injectors; activation delay and heal-over-time  
- **Unconsciousness & rescue** — critical downs, give-up flow, Adrenaline Injector rescue, and self-revival with a defibrillator
- **Adrenaline** — encounters, attacks, and moving projectile near misses drive combat feedback, pain relief, and post-combat Fatigue
- **Opioid addiction** — hidden addiction value, withdrawal episodes, status icons  
- **Client feedback** — pain blur, hit red pulse, morphine color grade, adrenaline rush blur, heartbeat audio  
- **Public extension API** — third-party treatment items and medicines  

---

## Screenshots

![Deal with the Devil — 1.3.0 poster](./screenshots/1.3.0_poster.png)

![The Rush and the Reckoning — 1.3.3 poster](./screenshots/1.3.3_poster.png)

![Pain effect](./screenshots/pain.png)

![UI health view](./screenshots/ui.png)

![Unconsciousness](./screenshots/unconsciousness.png)

---

## Extension API

Third-party mods can register custom treatment items and direct-use medicines:

- Common overview: [docs/firstaid-extension-api.md](./docs/firstaid-extension-api.md)
- Fabric: [docs/firstaid-extension-fabric.md](./docs/firstaid-extension-fabric.md)
- NeoForge: [docs/firstaid-extension-neoforge.md](./docs/firstaid-extension-neoforge.md)
- Forge 1.20.1: [docs/firstaid-extension-forge1.20.1.md](./docs/firstaid-extension-forge1.20.1.md)

---

## Command Setup Guide

Players with OP (or sufficient permission) receive a compact First Aid command tip on join; it now includes a clickable personal tinnitus entry. In the tip:

- **Click** a bracketed command to prefill chat  
- **Hover** for what it changes and a starter syntax  

### Quick start

```mcfunction
/firstaid pain dynamic
/firstaid adrenaline mild
/firstaid medicineeffect assisted
```

- `pain dynamic` — pain feedback follows injury severity  
- `adrenaline mild` — softer adrenaline feedback (`dynamic` is the default)
- `medicineeffect assisted` — paced medicine without full realistic harshness  

### Common commands

#### Pain

```mcfunction
/firstaid pain dynamic
/firstaid pain mild
```

#### Personal tinnitus

Available to every player without OP permission; changes affect only your own client. The login message shows the full syntax, and operator command tips include a clickable entry.

```mcfunction
/firstaid tinnitus
/firstaid tinnitus off
/firstaid tinnitus on
/firstaid tinnitus duration 75
```

The client config contains `tinnitusEnabled` (default `true`) and `tinnitusDurationPercent`
(default `75`, integer range `1`–`400`). Fabric stores these in
`config/firstaid/firstaid-client.json`; Forge and NeoForge use `config/firstaid-client.toml`
under `[Misc]`. Commands save immediately; file edits take effect after restarting the client.
Disabling tinnitus immediately stops injury and morphine ringing. Duration changes apply to
new sounds; values above 100 loop the clip without changing its pitch. At pitch 1, the default
75% lasts about 7.94 seconds. Suppression itself no longer triggers tinnitus.
Body-part injury triggers tinnitus on a new head hit if it removes more than 4 health points
after mitigation, or if the head's remaining health is at or below 40%. Small repeated head
hits therefore count once the injury is serious; damage to other parts cannot trigger it.
Head-injury ringing has a 10-second cooldown, reduced volume, and a base duration of
3–5 seconds depending on hit strength (2.25–3.75 seconds at the default 75%).
Explosion, poison, wither and morphine triggers remain available.

Head and torso injury feedback uses short effects instead of recurring nausea:

| Injury feedback | Hit strength or remaining part health | Duration |
| --- | --- | --- |
| Head coordination loss (Mining Fatigue I) | At least 2 damage or at most 40% health; at least 4 damage or at most 20% health | 2 / 3 seconds |
| Head vision blackout (Blindness) | At least 4 damage or at most 20% health; at least 6 damage or at most 10% health | 1 / 1.5 seconds |
| Head dimmed vision (Darkness) | At least 2 damage or at most 40% health; at least 4 damage or at most 20% health | 2 / 2.5 seconds |
| Torso breath interruption (Slowness I) | At least 2 damage or at most 35% health; at least 4 damage or at most 15% health | 1 / 1.5 seconds |
| Torso dimmed vision (Darkness) | At least 4 damage or at most 20% health | 2 seconds |
| Torso weakness (Weakness I) | Below 30% health | While injured; clears on healing to 30% |

The on-hit effects require new damage to that part and have a 4-second cooldown per part and effect.
The strongest matching tier applies once per hit. Fully absorbed hits do not count.
Injury darkness expires on its own; low health alone does not keep refreshing it.
Tinnitus fades to silence over its final 2 seconds (or its entire duration when shorter),
including explosion cues. Expiry disables looping and replay; pause, death, disconnect,
world changes and disabling tinnitus clear the sound immediately.
Datapacks may add `healthFractionThreshold` (0–1) to on-hit boundaries; omitted values
keep the previous damage-only behavior. Existing nausea from poison or withdrawal remains.

Feedback regression checks (run from the repository root with Python and a JDK on PATH):

```powershell
python tools/check_tinnitus.py
python tools/check_tinnitus_runtime.py
python tools/check_tinnitus_runtime.py --lifetime
python tools/check_suppression_parity.py
python tools/check_pain_blur.py
```

Suppression camera feedback is identical across all seven maintained modules. Horizontal
and vertical sway and near-miss kicks are about 40% of the previous amplitudes.
Vertical sway is subtle (about a quarter of the horizontal amplitude). A centered roll rocks
the horizon around the view axis at about 7.13 Hz (1.6 times the previous frequency)
without changing the facing direction. Its amplitude scales directly with suppression,
up to ±1.20° in dynamic mode or ±0.80° with default mild settings at full suppression, and is
zero without suppression; it has no constant tilt or accumulated directional roll. Near-miss kicks are
reduced, decay as before, and are capped at ±0.22° horizontally and ±0.06° vertically.
Adrenaline feedback uses a shared multiplier for the screen overlay and camera sway:
dynamic is 0.75, while mild is 1.25 times its configured base multiplier
(0.50 at the default base of 0.4), capped at 1.0. These display modes do not change combat
benefits. Suppression and near misses no longer narrow FOV. The server setting
`adrenalineReliefMultiplier` defaults to 1.7 (range 0–2) and scales injury debuff relief
and pain FOV relief. Set it to 1.0 to restore the previous relief, or 0 to disable these
two benefits. It does not change pain blur, sound, HUD pain indicators or drug pain relief.
Below 20% pressure, injury rules remain unchanged. Between 20% and 35%, the threshold
reduction increases gradually. With the default 1.7 multiplier and default injury data,
high-pressure Slowness starts at about 29% aggregate leg/foot health and Mining Fatigue at
32.5% aggregate arm health, both capped at level I. Thresholds already at or below 50%
are preserved, including stricter datapack thresholds. Fabric and NeoForge use matching
overlay parameters; NeoForge 26.2/26.3 renders the status overlay once through its HUD layer.
Adrenaline injection immediately sets suppression to 100%, holds it for 15 seconds,
and synchronizes the level to the client. It retains Absorption II, brief regeneration and rescue. Movement and attack speed
come from pressure alone, with visible Adrenaline Speed, Adrenaline Attack Speed and Adrenaline Strength effects
at levels I–V. Movement and attack speed gain 4% per level, up to 20%; Strength adds
0.6 attack damage per level, up to +3. External Speed or Strength suppresses the corresponding
pressure bonus to avoid stacking. It does not separately grant or extend Speed, Haste,
Strength or Painkiller. Existing external
effects remain intact. Severe relevant limb injuries still block their natural combat bonus;
critical head/torso injury or unconsciousness blocks both natural bonuses.
Melee hit pressure gain is about 10% higher than ranged-hit gain (6% versus 5.46% at
zero pressure with the default coefficient). Nearby mobs targeting you and recent player
attackers slowly replenish pressure once per second. Each additional threat adds 0.4
percentage points to the base recovery rate and 6 points to its ceiling, capped at six threats:
1–3% per second before diminishing returns, with default ceilings from 20% to 50%.
Recovery is scaled by remaining pressure headroom, respects the two-second post-injury delay,
and does not prolong higher attack- or injection-generated pressure. Initial visible-hostile
encounter activation remains unchanged; the configured base ceiling remains effective.
Actual injury costs 2 percentage points of pressure per heart lost, capped at 10 points
per hit; absorbed or cancelled damage costs nothing. After the hold period, nearby hostile
creatures or recent player attackers reduce pressure decay by 60%, from 7.5 to 3 percentage
points per second. This uses the configured encounter threat range (12 blocks by default)
and checks nearby danger once per second. Injury loss and the recovery delay still apply.
Suppression heartbeat starts above 5% intensity, follows the suppression decay, and
smoothly increases volume from 0.4 to 1.0 and playback speed from 0.95 to 1.20.
It fades out over 3 seconds after suppression ends; critical head or torso health still
triggers it. Rescue, death and disconnect retain immediate sound cleanup.

The first check executes the production injury trigger, debuff tiers, cooldowns, heartbeat,
injection, duration, lifecycle, camera and FOV code against minimal game API stubs.
It covers gradual head injury, strong hits, absorption, healing, immediate injection
synchronization, sustained suppression, repeated same-side shots, and recovery.
The runtime check launches each maintained module with its Gradle wrapper,
tests the real sound engine, permission-free command tree, camera event/Mixin hooks, FOV
calculations, heartbeat curves, injury JSON codecs and injection/head-injury sync data,
restores the client settings,
and exits the test client. Pass module names to check a subset, for example
`python tools/check_tinnitus_runtime.py fabric26.2 neoforge26.3`.
Runtime logs and results are written under each module's `build/` directory.

Pain blur eases toward its target and interpolates between ticks, including during
healing, pain relief, and effect-tier changes. On 26.2/26.3, a per-frame uniform updates
the blur pass; morphine color effects retain that pass while existing blur fades out.
The pain-blur check exercises transitions, interpolation, reset, and shader wiring
across all seven maintained modules.

#### Adrenaline

```mcfunction
/firstaid adrenaline dynamic
/firstaid adrenaline mild
/firstaid adrenaline off
```

Legacy `/firstaid suppression` commands remain available for existing server scripts.

```mcfunction
/firstaid adrenaline fatigue on
/firstaid adrenaline fatigue threshold 60
/firstaid adrenaline fatigue ratio 10
/firstaid adrenaline gain 0.15
/firstaid adrenaline relief 1.7
```

The `relief` command requires operator permission, updates the server configuration and
synchronizes clients immediately. `/firstaid suppression relief` is the legacy alias.
Forge/NeoForge clients and servers must both use the updated mod because the settings
packet now carries the relief multiplier. Delayed local-client settings replies cannot
overwrite newer integrated-server commands.

#### Random damage

```mcfunction
/firstaid randomdamage friendly chance 80
/firstaid randomdamage normal
```

#### Medicine timing

```mcfunction
/firstaid medicineeffect realistic
/firstaid medicineeffect assisted
/firstaid medicineeffect casual
```

#### Knockdown mode (admin)

```mcfunction
/firstaid knockdown
/firstaid knockdown chance
/firstaid knockdown always
/firstaid knockdown never
/firstaid knockdown coefficient 0.35
```

Chooses what happens when the head or body reaches 0 HP. `chance` (default) downs with probability `exp(-coefficient × overflow / part max HP)`, so exact kills always down and heavy overkill is more likely to be lethal; `always` always downs; `never` kills instead. The coefficient (0–5, default 0.35) only matters in `chance` mode. Every change shows a full-screen notice to online players and to everyone else on their next join.

#### Rescue wake-up delay

```mcfunction
/firstaid revivewakeup on 15
/firstaid revivewakeup off
```

#### Injury debuffs

```mcfunction
/firstaid injurydebuff normal
/firstaid injurydebuff low
/firstaid injurydebuff off
/firstaid injurydebuff minecraft:slowness off
```

#### Addiction (admin)

```mcfunction
/firstaid addiction set @s 0
```

Querying another player’s addiction is admin-only; operators can set values for balance testing.

#### Restore defaults (admin)

```mcfunction
/firstaid reset
```

Restore every First Aid server configuration value to its declared default, including body health, medicine, crafting, and join command tips.
The command saves and syncs the settings, updates online body-health caps, and reloads recipes. Player injuries, addiction, and personal client settings are preserved.
Values read during player or world initialization take effect after rejoining or restarting. If recipe reload fails, the default settings stay saved; fix the reported error and run `/reload`.

#### Body health (admin)

```mcfunction
/firstaid health body
/firstaid health head 8
/firstaid health left_arm 6
```

Use `head`, `body`, `left_arm`, `right_arm`, `left_leg`, `right_leg`, `left_foot`, or `right_foot`.
Omit the value to query; set 2–12 health points (2 points = 1 heart) to save the base maximum and immediately sync all online players.
Existing max-health attribute scaling and caps still apply. Injuries and treatments are preserved; lowering a maximum clamps current health, while raising it does not heal.
Offline players receive the new maxima when they next join. Requires operator level 2 or the equivalent gamemaster permission.

#### Medicine and crafting (admin)

```mcfunction
/firstaid addiction gain morphine
/firstaid crafting yield bandage 4
/firstaid crafting durability adrenaline_injector 4
```

### Recommended presets

**Survival-oriented**

```mcfunction
/firstaid pain dynamic
/firstaid adrenaline dynamic
/firstaid medicineeffect realistic
/firstaid revivewakeup on 15
/firstaid injurydebuff normal
```

**Balanced**

```mcfunction
/firstaid pain dynamic
/firstaid adrenaline mild
/firstaid medicineeffect assisted
/firstaid revivewakeup on 15
/firstaid injurydebuff low
```

**Casual**

```mcfunction
/firstaid pain mild
/firstaid adrenaline mild
/firstaid medicineeffect casual
/firstaid revivewakeup off
/firstaid injurydebuff low
```

### Debug

`/damagePart` is for testing locational damage, not normal play:

```mcfunction
/damagePart HEAD 4
/damagePart HEAD 4 nodebuff
```

---

## Morphine Injector craft

```
M M I
R B I
  I
```

- **M** — Morphine ×2  
- **I** — Iron Ingot  
- **R** — Redstone  
- **B** — Glass Bottle  

→ Morphine Injector (2 uses)

---

## Building

Each loader folder under this repository is its own Gradle project. From a module root (example: `forge1.20.1`):

```powershell
.\gradlew.bat build
```

Runnable jars are written to that module’s `build/libs/` and, for releases, collected under [`release/`](./release/).

Maintained module roots:

- `forge1.20.1`
- `fabric1.21.1`
- `neoforge1.21.1`
- `fabric26.2`
- `neoforge26.2`
- `fabric26.3`
- `neoforge26.3`

---

## Credits & License

- Based on **First Aid** by ichttt  
- This port is distributed under **GPL-3.0**, consistent with the original project  

Repository: [maoruiQa/FIrst-Aid-New](https://github.com/maoruiQa/FIrst-Aid-New)
