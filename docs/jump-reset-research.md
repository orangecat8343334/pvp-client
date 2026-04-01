# Jump Reset Mechanics - Deep Research

## 1. What Is Jump Resetting?

Sprint resetting by jumping after landing a hit. When you hit while sprinting, the game calls `setSprinting(false)` — your sprint is cancelled. Jumping re-engages sprint within 1 tick because the movement tick processes W held + sprint key → `setSprinting(true)`.

**Why it matters:** Sprint hits deliver ~2.25x the horizontal knockback of non-sprint hits. Without resetting sprint between hits, only your first hit gets the bonus knockback.

## 2. How Sprint Cancellation Works (Source Code)

In `PlayerEntity.attack()`:
```java
boolean wasSprinting = this.isSprinting();
// ... damage calculation ...
if (wasSprinting && !isCritical) {
    // Apply sprint knockback bonus (equivalent to Knockback I)
    target.takeKnockback(0.5F, sin(yaw), -cos(yaw));
}
// Sprint cancelled AFTER the hit:
this.setSprinting(false);
```

## 3. How Jumping Re-enables Sprint

In `ClientPlayerEntity.tickMovement()`:
```java
boolean canSprint = this.input.movementForward >= 0.8F;
// + hunger check (food > 6), no blindness, etc.
if (canSprint && !this.isSprinting()) {
    this.setSprinting(true);
}
```

Jumping triggers `tickMovement()` processing next tick. With W held + sprint key, sprint is re-enabled **within 1 tick**. This is the fastest possible sprint reset.

## 4. Tick-Level Sequence
```
Tick N:     Attack lands. setSprinting(false). Press SPACE.
Tick N+1:   tickMovement() → W held + sprint conditions → setSprinting(true)
Ticks N+2 to N+12: Sprint active, waiting for cooldown
Tick N+13:  Next sword attack ready (12.5 tick cooldown). Hit WITH sprint bonus.
```

## 5. W-Tap vs S-Tap vs Jump Reset

### Jump Reset
- **Method:** Press SPACE after hit
- **Reset time:** 1 tick (fastest)
- **Pros:** No momentum loss, continuous forward pressure, easiest to execute
- **Cons:** Airborne = vulnerable to crits, predictable pattern, Y-position changes

### W-Tap
- **Method:** Release W briefly, re-press
- **Reset time:** ~2 ticks minimum
- **Pros:** Stay grounded, consistent reach, harder to crit you
- **Cons:** Brief momentum loss, harder execution

### S-Tap
- **Method:** Tap S briefly, then W
- **Reset time:** ~3 ticks
- **Pros:** Creates spacing, strongest sprint reset, punishes rushers
- **Cons:** Lose ground position, hardest execution, can miss next hit if too far

### When Each Is Best
| Situation | Best Method |
|-----------|------------|
| Fast combos | Jump Reset |
| Trading hits | W-Tap |
| Opponent retreating | Jump Reset |
| Opponent rushing | S-Tap |
| Combo extending | Jump Reset |
| Anti-crit play | W-Tap |

## 6. Knockback Formula (Critical for Understanding)

```java
void takeKnockback(double strength, double x, double z) {
    Vec3d vel = this.getVelocity();
    Vec3d kb = new Vec3d(x, 0, z).normalize().multiply(strength);
    this.setVelocity(
        vel.x / 2.0 - kb.x,      // Current velocity HALVED then KB added
        onGround ? min(0.4, vel.y / 2.0 + strength) : vel.y,
        vel.z / 2.0 - kb.z
    );
}
```

**Key insights:**
- Current velocity is **halved** before new KB is added — this is why combos work (halving + new KB compounds)
- Y component capped at 0.4 if on ground
- Sprint KB adds extra `takeKnockback(0.5, sin(yaw), -cos(yaw))` — roughly **2.25x** horizontal KB vs non-sprint

### Knockback Stacking in Combos
1. Hit 1 (sprint): Target launched with full KB
2. Target decelerates (0.91 air drag per tick)
3. Hit 2 (sprint reset): Target velocity halved, THEN new KB added on top
4. Compound effect: each hit sends them further because you're adding KB to already-moving target

**Without sprint reset:** Hit 2 only gets base KB (0.4 strength instead of 0.4+0.5). Target barely moves. Combo breaks.

## 7. Ground vs Air Hits

### Ground Hit (Pre-Jump) — IDEAL
- Full sprint KB applied
- Target Y velocity set to `min(0.4, ...)` — launched upward
- Most consistent KB delivery

### Air Hit (Rising — just jumped)
- Sprint KB still applies (sprint re-enabled from jump)
- NOT a crit (Y velocity positive, not falling)
- Normal sprint hit from air

### Air Hit (Falling — apex of jump)
- CAN be a crit (if fallDistance > 0, not on ground, not sprinting)
- But wait: sprint and crits are **mutually exclusive** (`!isSprinting()` required for crit)
- To get crit + KB: release sprint before hitting while falling

## 8. Sprint-Jump Velocity Boost

| State | Horizontal Speed (blocks/tick) |
|-------|-------------------------------|
| Walking | ~0.108 |
| Sprinting | ~0.140 |
| Sprint-jumping | ~0.252 (burst on jump tick) |

Sprint-jump adds **+0.2 blocks/tick** horizontal boost:
```java
velX += -sin(yaw) * 0.2;
velZ += cos(yaw) * 0.2;
```
This means jump resets actually INCREASE closing speed on opponents.

## 9. Advanced Techniques

### Crit-Chain with Jump Reset
1. Hit (sprint cancelled) → Jump to reset sprint
2. Time NEXT hit for falling portion of jump arc
3. Release sprint just before hitting → **crit (1.5x) + sprint momentum KB**
4. Apex of jump is ~6 ticks after jumping

### Directional KB Manipulation
- KB direction based on `sin(attackerYaw)` and `cos(attackerYaw)` at hit time
- Angle your yaw to steer opponents into walls/edges
- Look slightly left/right of target to deflect their trajectory

### Strafing While Jump Resetting
- Circle-strafe makes you harder to hit while maintaining sprint-KB pressure
- KB direction = attacker yaw, NOT movement direction
- Alternate strafe direction every 2-3 hits for unpredictability

## 10. Server-Side vs Client-Side

### Client-Side
- Hit detection (raycast + packet sent)
- Sprint state activation (input processing in `tickMovement()`)
- Jump input processing

### Server-Side
- Damage calculation (`PlayerEntity.attack()`)
- Knockback application (`takeKnockback()`)
- Sprint state verification (entity metadata sync)
- Attack cooldown tracking

### Latency Effects
- Sprint state sync: ~1 tick behind at minimum (metadata updates at 20Hz)
- High ping: server may not see sprint=true by the time next attack packet arrives
- Velocity packets from server can be "eaten" by client-side prediction

### Packet Sequence
```
Client                              Server
  |-- AttackEntityC2S ------------->|  (hit)
  |-- Metadata (sprint=false) ----->|  (auto-cancelled)
  |   [JUMP processed locally]      |
  |-- Metadata (sprint=true) ------>|  (re-enabled)
  |                                 |-- Processes attack, applies KB
  |                                 |-- VelocityUpdateS2C -> target
```

## 11. Implementation Plan for Our Mod

### Phase 1: Basic Jump Reset (Current skeleton)
- Detect hit via `MinecraftClientMixin.onDoAttack()`
- Inject jump input on same/next tick
- Force sprint re-enable

### Phase 2: W-Tap / S-Tap Support
- Mixin into `KeyboardInput.tick()` to manipulate movement keys
- W-Tap: suppress forward for N ticks, then release
- S-Tap: inject backward for N ticks, then release
- Track key state to restore properly

### Phase 3: Crit Integration
- Detect jump apex (~6 ticks after jump)
- Release sprint on falling portion for crit eligibility
- Time attack for max fall distance + full cooldown
- Combine: sprint momentum KB + crit damage (1.5x)

### Phase 4: Smart Mode
- Analyze opponent position/velocity
- Choose best method dynamically (jump/W-tap/S-tap)
- Opponent retreating → jump reset (maintain pressure)
- Opponent rushing → S-tap (create space)
- Trading hits → W-tap (ground stability)

### Key Mixins Needed
```java
// 1. Detect attacks
@Mixin(ClientPlayerInteractionManager.class)
void onAttack(...) → JumpResetManager.onAttackEntity()

// 2. Inject jump/movement keys
@Mixin(KeyboardInput.class)
void onTick(...) → set jumping=true / suppress forward / inject backward

// 3. Monitor sprint state
@Mixin(ClientPlayerEntity.class)
void afterTickMovement(...) → verify sprint re-enabled
```

### Config Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| resetMethod | JUMP | JUMP, W_TAP, S_TAP |
| jumpDelayTicks | 0 | Ticks after hit before jumping (0=same tick) |
| wTapReleaseTicks | 1 | Ticks to release W |
| sTapDurationTicks | 2 | Ticks to hold S |
| autoCrit | false | Time hits for fall phase (crit + sprint KB) |
| autoSprint | true | Keep sprint forced on |
| minCooldownProgress | 0.9 | Min cooldown before allowing next hit |
