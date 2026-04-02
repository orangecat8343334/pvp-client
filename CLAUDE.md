# PVP Client - Codex Guide

## Project Overview
Minecraft 1.9+ PVP assistance mod (Fabric 1.21, Java 21). Five core features:
- **TriggerBot** - Auto-attack at optimal cooldown timing with weapon-specific delays
- **JumpReset** - Offensive sprint reset via jump/W-tap/S-tap after landing hits
- **ShieldDisable** - Auto axe-switch to disable opponent shields (with facing check)
- **DefensiveJumpReset** - Jump when YOU get hit to reduce knockback taken
- **NoMissDelay** - Cancel attacks on air/blocks to preserve cooldown

## Project Structure
- `src/main/java/com/pvpclient/`
  - `PvpClientMod.java` - Entry point, keybinds, tick loop, `notifyAttackLanded()` dispatch
  - `config/PvpConfig.java` - JSON config with per-module settings
  - `triggerbot/TriggerBot.java` - State machine: IDLE→WAITING_COOLDOWN→WAITING_CRIT→READY→ATTACKING
  - `triggerbot/NoMissDelay.java` - Static utility to check if attack should be cancelled
  - `jumpreset/JumpResetManager.java` - IDLE→HIT_LANDED→RESETTING→RECOVERING, with input snapshot/restore
  - `jumpreset/DefensiveJumpReset.java` - Jump at hurtTime==9 to reduce KB (from argon)
  - `shielddisable/ShieldDisableManager.java` - IDLE→DETECTED_SHIELD→SWITCHING_TO_AXE→ATTACKING→SWITCHING_BACK→COOLDOWN
  - `gui/PvpSettingsScreen.java` - In-game settings GUI
  - `gui/PvpHudOverlay.java` - HUD overlay showing all module status + cooldown %
  - `util/TimingUtil.java` - Millisecond-precision timing (nanoTime-based)
  - `util/CombatUtil.java` - Combat helpers: cooldown, crit, shield facing, target validation
  - `mixin/ClientPlayerEntityMixin.java` - tickMovement HEAD/RETURN for W-tap/S-tap key injection
  - `mixin/ClientPlayerInteractionManagerMixin.java` - attackEntity TAIL for JumpReset notification
  - `mixin/ClientPlayerInteractionManagerAccessor.java` - @Invoker for syncSelectedSlot (shield swap)
  - `mixin/MinecraftClientMixin.java` - doAttack HEAD for NoMissDelay cancellation
- `docs/` - Deep research docs on all combat mechanics

## Key Architecture Patterns

### Module Coordination
- TriggerBot yields to ShieldDisable via `shouldYieldToShieldDisable()` → `shouldTakePriority()`
- ShieldDisable checks cooldown BEFORE switching to axe (don't hold axe while waiting)
- Attack notifications flow: Mixin → `PvpClientMod.notifyAttackLanded()` → `JumpResetManager.onAttackEntity()`

### Input Manipulation (W-tap/S-tap)
- JumpResetManager captures input snapshot before manipulation
- ClientPlayerEntityMixin HEAD: apply key overrides (suppress W / inject S)
- ClientPlayerEntityMixin RETURN: restore original key state
- finishReset() explicitly restores before clearing snapshot (prevents race)

### Shield Facing Check
- `CombatUtil.isShieldFacingAway()` — dot product of target facing vs direction to attacker
- `CombatUtil.isShieldBlockingUs()` — target is blocking AND shield faces us
- ShieldDisable only triggers when shield is FACING us (skip if we can hit from behind)

## Key Constraints
- ALL timing is millisecond-based (System.nanoTime()), NEVER tick-based
- Attack cooldown uses `getAttackCooldownProgress(0.5f)`
- Crits require: fallDistance > 0 + !onGround + !sprinting + !climbing + !submergedInWater + !blindness + cooldown > 0.9
- Sprint and crits are MUTUALLY EXCLUSIVE
- Shield disable requires axe + cooldown >= 90% (does NOT require sprinting)
- Cooldown is tracked per-PLAYER not per-weapon (switching weapons doesn't reset it)

## Keybinds
- R = Toggle TriggerBot
- J = Toggle Jump Reset
- G = Toggle Shield Disable
- K = Toggle KB Reduce (Defensive Jump Reset)
- ; = Open Settings

## Build
```bash
./gradlew build
```

## Reference
- Argon (archived): github.com/LvStrnggg/argon — referenced for combat patterns
- Research docs in `docs/` folder
