# PVP Client - Codex Guide

## Project Overview
Minecraft 1.9+ PVP assistance mod (Fabric 1.21, Java 21). Three core modules:
- **TriggerBot** - Auto-attack at optimal cooldown timing
- **JumpReset** - Sprint reset via jump/W-tap/S-tap after hits
- **ShieldDisable** - Auto axe-switch to disable opponent shields

## Project Structure
- `src/main/java/com/pvpclient/` - Fabric mod source
  - `PvpClientMod.java` - Entry point, keybind registration, tick loop
  - `config/PvpConfig.java` - JSON config with per-module settings
  - `triggerbot/TriggerBot.java` - State machine: IDLE→WAITING_COOLDOWN→WAITING_CRIT→READY→ATTACKING
  - `jumpreset/JumpResetManager.java` - State machine: IDLE→HIT_LANDED→RESETTING→RECOVERING
  - `shielddisable/ShieldDisableManager.java` - State machine: IDLE→DETECTED_SHIELD→SWITCHING_TO_AXE→ATTACKING→SWITCHING_BACK→COOLDOWN
  - `gui/PvpSettingsScreen.java` - In-game settings GUI
  - `gui/PvpHudOverlay.java` - HUD overlay showing module status
  - `util/TimingUtil.java` - Millisecond-precision timing (nanoTime-based)
  - `util/CombatUtil.java` - Combat helpers (cooldown, crit check, target detection, weapon finding)
  - `mixin/` - Fabric mixins for hooking into game events

## Key Constraints
- ALL timing is millisecond-based (System.nanoTime()), NEVER tick-based
- Attack cooldown uses Minecraft's built-in `getAttackCooldownProgress()`
- Sprint reset must happen between hits to maintain knockback advantage
- Shield disable requires: detect block → switch to axe → attack → switch back (all within ticks)
- Crits require: falling + not on ground + not sprinting + not in water + not climbing

## Build
```bash
./gradlew build
```

## Keybinds (defaults)
- R = Toggle TriggerBot
- J = Toggle Jump Reset
- G = Toggle Shield Disable
- ; = Open Settings
