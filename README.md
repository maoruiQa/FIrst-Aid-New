# First Aid New

First Aid New is a port of the original **First Aid** mod by ichttt, updated for modern Minecraft versions and loaders, with expanded realism features, stronger visual effects, and new mechanics such as unconsciousness.

Original project: https://www.curseforge.com/minecraft/mc-mods/first-aid

## Features
- Split-body health system (head, torso, arms, legs, etc.)
- More realistic survival mechanics
- Enhanced vision effects for injuries and status effects
- Additional mechanics (e.g., unconsciousness)

## Supported Minecraft Versions
- 1.21.1
- 1.21.11

## Supported Loaders
- NeoForge
- Fabric

## Command Setup Guide

Players with OP or sufficient command permissions now receive a compact First Aid command tip when they join the game.
The in-game tip supports two interactions:

- Click a bracketed command to prefill the chat box with a recommended command.
- Hover a bracketed command to see what it changes and which syntax to start from.

### Quick Start

If you want a solid default setup first, start with these three commands:

```mcfunction
/firstaid pain dynamic
/firstaid suppression dynamic
/firstaid medicineeffect assisted
```

This setup keeps the mod readable without flattening its identity:

- `pain dynamic`: pain feedback follows injury severity instead of staying permanently soft.
- `suppression dynamic`: suppression reacts to combat pressure for a stronger battlefield feel.
- `medicineeffect assisted`: medicine still has pacing, but it is less punishing than `realistic`.

### Common Commands

#### Pain Intensity

```mcfunction
/firstaid pain dynamic
/firstaid pain mild
```

- `dynamic`: pain-related feedback scales with how badly the player is hurt.
- `mild`: keeps pain effects lighter for more casual servers.

#### Suppression Intensity

```mcfunction
/firstaid suppression dynamic
/firstaid suppression mild
```

- `dynamic`: suppression changes with combat pressure.
- `mild`: keeps suppression lighter if you want less aggressive visual disruption.

#### Medicine Timing

```mcfunction
/firstaid medicineeffect realistic
/firstaid medicineeffect assisted
/firstaid medicineeffect casual
```

- `realistic`: slowest and most survival-focused.
- `assisted`: recommended default for a balanced experience.
- `casual`: fastest activation and the least demanding option.

#### Rescue Wake-Up Delay

```mcfunction
/firstaid revivewakeup on 15
/firstaid revivewakeup off
```

- `on <seconds>`: rescued players stay down until the delay expires.
- `off`: rescued players stand up immediately.

If you want rescue to create a tactical recovery window, start with `on 15` or `on 20`.

### Advanced Tuning

#### Injury Debuffs

```mcfunction
/firstaid injurydebuff normal
/firstaid injurydebuff low
/firstaid injurydebuff off
/firstaid injurydebuff minecraft:slowness off
```

- The first three commands change injury debuffs globally.
- `minecraft:slowness off` shows how to disable a single effect without changing the rest.
- A good workflow is to keep the global mode at `normal` and then override only the effects you want to soften or disable.

### Debug Command

`/damagePart` is mainly for testing locational damage behavior and is not intended for normal players.

```mcfunction
/damagePart HEAD 4
/damagePart HEAD 4 nodebuff
```

- The first command applies locational damage to the head.
- The second command skips the linked debuff, which is useful when you only want to test damage values or UI feedback.

### Recommended Presets

#### Survival-Oriented Server

```mcfunction
/firstaid pain dynamic
/firstaid suppression dynamic
/firstaid medicineeffect realistic
/firstaid revivewakeup on 20
/firstaid injurydebuff normal
```

#### Balanced Server

```mcfunction
/firstaid pain dynamic
/firstaid suppression dynamic
/firstaid medicineeffect assisted
/firstaid revivewakeup on 15
/firstaid injurydebuff low
```

#### Casual Server

```mcfunction
/firstaid pain mild
/firstaid suppression mild
/firstaid medicineeffect casual
/firstaid revivewakeup off
/firstaid injurydebuff low
```

## Screenshots

![Pain effect](./screenshots/pain.png)
![UI health view](./screenshots/ui.png)
![Unconsciousness](./screenshots/unconsciousness.png)

## Credits & License
- Based on **First Aid** by ichttt.
- This port is distributed under the **GPL-3.0** license, consistent with the original project.
