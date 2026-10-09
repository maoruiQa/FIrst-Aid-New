First Aid feedback update - 2026-10-08

Select the JAR matching your Minecraft version and loader and replace the previous First Aid
JAR in your instance's mods folder. These builds still use version number 1.3.3.

All seven maintained modules share these changes:
- Fabric client command handling now lets other server-side `/firstaid` branches pass through while the personal `tinnitus` branch stays local.
- Centered view-axis roll scales with suppression, is capped at +/-1.6 degrees and runs
  at about 7.13 Hz at 20 TPS (1.6 times the previous frequency). Roll preserves facing.
- Horizontal/vertical sway stays subtle. Near-miss kicks remain capped at +/-0.22 degrees
  yaw and +/-0.06 degrees pitch, with the existing decay.
- Suppression FOV reduction increases another 10% from the preceding build (1.21 times
  the reduction before both adjustments). Near misses add no separate FOV reduction.
- Suppression heartbeat starts above 5%, follows decay, and smoothly increases its volume
  from 0.4 to 1.0 and speed from 0.95 to 1.20. It fades out over 3 seconds.
- Head-injury tinnitus triggers on new head damage if the hit removes more than 4 health
  points OR head health is at most 40%. Gradual injury now counts. Other part hits cannot
  trigger head tinnitus. Cooldown: 10 seconds; base duration: 3-5 seconds, reduced volume.
  At the default 75% setting, head ringing lasts 2.25-3.75 seconds. Existing explosion,
  poison, wither, morphine and personal settings remain.
- Default head/torso nausea is removed. Head hits cause brief coordination loss or,
  for serious trauma, a 1-1.5 second vision blackout. Torso hits cause a brief 1-1.5 second
  slowdown; Weakness I applies below 30% torso health and clears after healing to 30%.
  Head hits also cause Darkness for 2 seconds at >=2 damage or <=40% remaining head
  health, or 2.5 seconds at >=4 damage or <=20% health. Torso hits cause Darkness for
  2 seconds at >=4 damage or <=20% remaining torso health.
  These on-hit effects use hit strength OR remaining part health and a 4-second cooldown.
  Fully absorbed hits do not trigger injury effects.
  Low health alone cannot sustain injury Darkness; it expires without another qualifying hit.
- Adrenaline injection immediately sets suppression to 100%, holds it for 15 seconds,
  and synchronizes the level. Its existing gameplay implementation was verified.

The join command tip now includes a clickable Personal tinnitus entry.

The client login message now shows the full personal command syntax; operator tips also include a clickable tinnitus entry.

Personal commands (no administrator permission needed):
  /firstaid tinnitus
  /firstaid tinnitus on
  /firstaid tinnitus off
  /firstaid tinnitus duration 75
Settings save immediately to the existing local client config. Percent range: 1-400.

Run from the repository root:
  python tools/check_tinnitus.py
  python tools/check_tinnitus_runtime.py

For the native startup issue affecting Fabric 26.3, the passing probe used:
  $env:JAVA_TOOL_OPTIONS = '-Dmax.bg.threads=1'
  python tools/check_tinnitus_runtime.py fabric26.3

See the repository README for exact injury tiers and verification.txt for validation and hashes.
