# First Aid New

First Aid New is a multi-loader port of ichttt’s classic **First Aid**, rebuilt for modern Minecraft. It replaces the single vanilla health bar with per-body-part damage, injury debuffs, timed medicine, unconsciousness and rescue, and a full client feedback layer—pain, adrenaline, heartbeat audio, and HUD overlays that make survival feel physical again.

Original project: [First Aid on CurseForge](https://www.curseforge.com/minecraft/mc-mods/first-aid)

---

## 1.3.3 — *The Rush and the Reckoning*

![First Aid New 1.3.3 — The Rush and the Reckoning concept poster](./screenshots/1.3.3_poster.png)

*Danger brings the rush. Survival brings the reckoning.*

**1.3.3** turns adrenaline into a complete encounter cycle. Seeing a hostile creature, fighting, or narrowly avoiding a moving projectile can raise it; sustained pressure brings pain relief and combat boosts. Once the threat is gone, a long rush can give way to Fatigue. The Adrenaline Injector now delivers a full rush and can rescue a downed player. Recent releases also brought more responsive pain, self-defibrillator rescue on supported builds, and adjustable medicine and crafting.

### What's new

- **Adrenaline that follows danger:** Visible hostiles, nearby attackers, valid attacks, and moving projectile near misses can build pressure. Feedback now scales linearly, while medium and high adrenaline grant Haste I and Strength I respectively.
- **Relief with an aftermath:** Adrenaline pain relief clears First Aid injury debuffs while active. After 60 continuous seconds of adrenaline, leaving danger can trigger a short Fatigue episode; server owners can configure it.
- **A stronger last resort:** Adrenaline Injectors fill the meter, have four uses by default, and can revive a downed player. On builds other than 26.1, Defibrillators can also be used for self-revival while downed.
- **Medicine and crafting controls:** 1.3.2 added commands for addiction gain, recipe yields, and newly crafted device uses on supported modules; Bandages and Plaster now craft four by default there.
- **Clearer pain and audio:** Recent hits create a short pain spike over ongoing injury pain, while rescue and respawn clear lingering heartbeat and ringing sounds.

### Supported builds (1.3.3)

| Artifact | Loader | Minecraft |
|----------|--------|-----------|
| `firstaid-1.3.3+forge1.20.1` | Forge | 1.20.1 |
| `firstaid-1.3.3+fabric1.21.1` | Fabric | 1.21.1 |
| `firstaid-1.3.3+neoforge1.21.1` | NeoForge | 1.21.1 |
| `firstaid-1.3.3+fabric26.1` | Fabric | 26.1 |
| `firstaid-1.3.3+neoforge26.1` | NeoForge | 26.1 |
| `firstaid-1.3.3+fabric26.2` | Fabric | 26.2 |
| `firstaid-1.3.3+neoforge26.2` | NeoForge | 26.2 |
| `firstaid-1.3.3+fabric26.3` | Fabric | 26.3 |
| `firstaid-1.3.3+neoforge26.3` | NeoForge | 26.3 |

The 1.21.11 modules are legacy-only. The 26.1 builds do not include the 1.3.2 crafting changes, Morphine Injector, or self-defibrillator rescue; see the release notes for details.

Full notes: [1.3.3](./1.3.3changelog.md) · [1.3.2](./1.3.2changelog.md) · [1.3.1](./1.3.1changelog.md)

---

## Features

- **Locational health** — head, body, arms, legs, feet; each with its own pool and overflow rules  
- **Injury debuffs** — limb damage that changes how you move, dig, and fight  
- **Medicine with pacing** — bandages, plaster, painkillers, morphine, injectors; activation delay and heal-over-time  
- **Unconsciousness & rescue** — critical downs, give-up flow, Adrenaline Injector rescue, and self-revival with a defibrillator on builds other than 26.1
- **Adrenaline** — encounters, attacks, and moving projectile near misses drive combat feedback, pain relief, and post-combat Fatigue
- **Opioid addiction** — hidden addiction value, withdrawal episodes, status icons  
- **Client feedback** — pain blur, hit red pulse, morphine color grade, adrenaline rush blur, heartbeat audio  
- **Public extension API** — third-party treatment items and medicines  

---

## Screenshots

![Deal with the Devil — 1.3.0 poster](./screenshots/1.3.0_poster.png)

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

Players with OP (or sufficient permission) receive a compact First Aid command tip on join. In the tip:

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
```

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

#### Medicine and crafting (admin; not available on 26.1)

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

The 26.1 modules do not include the Morphine Injector.

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
- `fabric26.1`
- `neoforge26.1`
- `fabric26.2`
- `neoforge26.2`
- `fabric26.3`
- `neoforge26.3`

---

## Credits & License

- Based on **First Aid** by ichttt  
- This port is distributed under **GPL-3.0**, consistent with the original project  

Repository: [maoruiQa/FIrst-Aid-New](https://github.com/maoruiQa/FIrst-Aid-New)
