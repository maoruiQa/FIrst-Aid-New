# First Aid New

First Aid New is a multi-loader port of ichttt’s classic **First Aid**, rebuilt for modern Minecraft. It replaces the single vanilla health bar with per-body-part damage, injury debuffs, timed medicine, unconsciousness and rescue, and a full client feedback layer—pain, adrenaline, heartbeat audio, and HUD overlays that make survival feel physical again.

Original project: [First Aid on CurseForge](https://www.curseforge.com/minecraft/mc-mods/first-aid)

---

## 1.3.6

**1.3.6** gives server administrators control over combat adrenaline, so horde-heavy modpacks no longer push every fight to tier V. It applies across all seven maintained builds.

### What's new

- **Tier cap:** `/firstaid adrenaline maxlevel <0-5>` (`adrenalineMaxLevel`) sets the highest tier that hits, near misses and nearby enemies can build. Injectors still reach full strength.
- **Hit tuning:** `attackgain <0-2>` scales adrenaline from hitting enemies (`0` turns it off), `attackcooldown <ticks>` spaces out counted hits for fast weapons, and `attackhold <ticks>` sets how long adrenaline holds after a hit.
- **One swing, one hit:** Hits in the same tick (sweeps, multishot, piercing) now count once. Sweeping four mobs reaches tier V after about 13 seconds instead of 3; single-target combat is unchanged.
- **Forge 1.20.1 fix:** Projectile near misses add adrenaline again, as on the other loaders.
- Suggested preset for horde-heavy packs: `maxlevel 2`, `attackgain 0.5`, `attackcooldown 10` (see Adrenaline in the Command Setup Guide).

### Supported builds (1.3.6)

| Artifact | Loader | Minecraft |
|----------|--------|-----------|
| `firstaid-1.3.6+forge1.20.1` | Forge | 1.20.1 |
| `firstaid-1.3.6+fabric1.21.1` | Fabric | 1.21.1 |
| `firstaid-1.3.6+neoforge1.21.1` | NeoForge | 1.21.1 |
| `firstaid-1.3.6+fabric26.2` | Fabric | 26.2 |
| `firstaid-1.3.6+neoforge26.2` | NeoForge | 26.2 |
| `firstaid-1.3.6+fabric26.3` | Fabric | 26.3 |
| `firstaid-1.3.6+neoforge26.3` | NeoForge | 26.3 |

The 1.21.11 and 26.1 modules are legacy-only and no longer supported. The network protocol is unchanged from 1.3.5 (Forge `7.1`); the new settings are server-side.

Full notes: [1.3.6](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.6changelog.md) · [1.3.5](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.5changelog.md) · [1.3.4](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.4changelog.md) · [1.3.3](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.3changelog.md) · [1.3.2](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.2changelog.md) · [1.3.1](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/1.3.1changelog.md)

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

![Deal with the Devil — 1.3.0 poster](https://raw.githubusercontent.com/maoruiQa/FIrst-Aid-New/main/screenshots/1.3.0_poster.png)

![The Rush and the Reckoning — 1.3.3 poster](https://raw.githubusercontent.com/maoruiQa/FIrst-Aid-New/main/screenshots/1.3.3_poster.png)

![Pain effect](https://raw.githubusercontent.com/maoruiQa/FIrst-Aid-New/main/screenshots/pain.png)

![UI health view](https://raw.githubusercontent.com/maoruiQa/FIrst-Aid-New/main/screenshots/ui.png)

![Unconsciousness](https://raw.githubusercontent.com/maoruiQa/FIrst-Aid-New/main/screenshots/unconsciousness.png)

---

## Extension API

Third-party mods can register custom treatment items and direct-use medicines:

- Common overview: [docs/firstaid-extension-api.md](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/docs/firstaid-extension-api.md)
- Fabric: [docs/firstaid-extension-fabric.md](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/docs/firstaid-extension-fabric.md)
- NeoForge: [docs/firstaid-extension-neoforge.md](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/docs/firstaid-extension-neoforge.md)
- Forge 1.20.1: [docs/firstaid-extension-forge1.20.1.md](https://github.com/maoruiQa/FIrst-Aid-New/blob/main/docs/firstaid-extension-forge1.20.1.md)

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
python tools/check_adrenaline_limits.py
python tools/check_adrenaline_runtime.py
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

Combat adrenaline limits (operator only, saved immediately to the server config):

```mcfunction
/firstaid adrenaline maxlevel 5
/firstaid adrenaline attackgain 1
/firstaid adrenaline attackcooldown 0
/firstaid adrenaline attackhold 40
```

| Command | Config key | Range (default) | Effect |
|---------|------------|-----------------|--------|
| `maxlevel <0-5>` | `adrenalineMaxLevel` | 0–5 (5) | Highest tier that hits, projectile near misses and nearby enemies can build. `0` keeps combat below tier I. Adrenaline and morphine injectors still reach full strength. |
| `attackgain <0-2>` | `adrenalineAttackGainMultiplier` | 0–2 (1) | Scales adrenaline gained from hitting enemies, on top of `gain`. `0` disables hit-driven adrenaline. |
| `attackcooldown <ticks>` | `adrenalineAttackCooldownTicks` | 0–200 (0) | Minimum ticks between counted hits per player (20 ticks = 1 second). |
| `attackhold <ticks>` | `adrenalineAttackHoldTicks` | 0–200 (40) | How long adrenaline holds after a hit before it starts to decay. |

`gain` (`suppressionGainMultiplier`) scales both hits and near misses; `encounterBaseIntensity` only sets the
passive level from seeing hostiles and does not affect hit gain. Hits that land in the same tick (sweeps,
multishot, piercing) always count as one attack.

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

**Horde-heavy modpacks** (many enemies on screen; add to any preset above)

```mcfunction
/firstaid adrenaline maxlevel 2
/firstaid adrenaline attackgain 0.5
/firstaid adrenaline attackcooldown 10
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

Runnable jars are written to that module’s `build/libs/` and, for releases, collected under [`release/`](https://github.com/maoruiQa/FIrst-Aid-New/tree/main/release/).

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
