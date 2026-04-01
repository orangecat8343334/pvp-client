# PVP Client

Advanced Minecraft 1.9+ PVP assistance mod built with Fabric for 1.21.

## Features

### TriggerBot
Automatically attacks when your crosshair is on a target and your attack cooldown is ready. Configurable cooldown threshold, crit-only mode, humanized delay with jitter.

### Jump Reset (Sprint Reset)
Automatically resets your sprint after landing hits to maintain maximum knockback. Supports three methods:
- **Jump Reset** - Jump after hit to reset sprint
- **W-Tap** - Brief forward key release to break and re-engage sprint
- **S-Tap** - Brief backward tap for fastest sprint reset

### Shield Disable
Detects when opponents are blocking with shields and automatically switches to axe, attacks to disable the shield (5s cooldown), then switches back to sword.

## Installation
1. Install Fabric Loader for Minecraft 1.21
2. Install Fabric API
3. Drop `pvp-client-x.x.x.jar` into your `.minecraft/mods/` folder

## Keybinds
| Key | Action |
|-----|--------|
| R | Toggle TriggerBot |
| J | Toggle Jump Reset |
| G | Toggle Shield Disable |
| ; | Open Settings |

## Building
```bash
./gradlew build
```
Output jar will be in `build/libs/`.

## License
MIT
