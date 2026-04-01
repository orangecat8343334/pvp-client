# TriggerBot, Perfect Hits & Crits - Deep Research

## 1. Attack Cooldown System

### Formula
```
attackStrength = clamp((ticksSinceLastAttack + partialTick) / cooldownPeriod, 0.0, 1.0)
cooldownPeriod = 20.0 / attackSpeedAttribute
```

Player base attack speed = 4.0, modified by held weapon.

### Cooldown Per Weapon
| Weapon | Attack Speed | Cooldown (ticks) | Cooldown (ms) |
|--------|-------------|-------------------|---------------|
| Sword | 1.6 | 12.5 | 625ms |
| Diamond/Netherite Axe | 1.0 | 20 | 1000ms |
| Iron Axe | 0.9 | ~22.2 | ~1110ms |
| Wood/Stone Axe | 0.8 | 25 | 1250ms |
| Trident | 1.1 | ~18.2 | ~910ms |
| Fist/non-weapon | 4.0 | 5 | 250ms |

### Cooldown Damage Penalty (QUADRATIC)
```
cooldownMultiplier = 0.2 + (attackStrength^2) * 0.8
```
| Charge % | Damage Multiplier |
|----------|-------------------|
| 0% | 20% |
| 50% | 40% |
| 75% | 65% |
| 90% | 84.8% |
| 100% | 100% |

**Key insight:** The penalty is quadratic — the last 10% of cooldown matters a LOT. Attacking at 90% still loses 15% damage.

## 2. Critical Hits — ALL Conditions Required

1. `player.fallDistance > 0.0F` — must be falling
2. `!player.onGround()` — not on ground
3. `!player.onClimbable()` — not on ladder/vine
4. `!player.isInWater()` — not in water
5. `!player.hasEffect(BLINDNESS)` — no blindness
6. `!player.isPassenger()` — not riding
7. **`!player.isSprinting()`** — NOT sprinting (KEY condition many miss!)
8. `attackStrength > 0.9` — must be >90% charged

**Crit multiplier: 1.5x** applied to base damage after cooldown penalty.

### Sprint/Crit Mutual Exclusion
Sprinting and critting are **mutually exclusive** in vanilla. Sprint hits give knockback but NOT crit damage. To get crits:
- Release sprint before hitting (W-tap/S-tap)
- Jump to get fall distance
- Attack while falling but NOT sprinting

## 3. Hit Registration

### Client-Side Ray Trace
1. Ray cast from eye position along look vector
2. Range: **3.0 blocks** survival, 6.0 creative (1.21+)
3. Test against entity AABBs (axis-aligned bounding boxes)
4. Closest entity wins

### Player Hitbox
- Standing: 0.6 wide x 1.8 tall
- Sneaking: 0.6 wide x 1.5 tall

### Distance Measurement
From **player eye position** to **nearest point on target's AABB** (NOT center-to-center).

### Server Validation
- Server reach check: **6.0 blocks** (lenient for latency — double the client check)
- Anti-cheat plugins tighten to ~3.1-3.5 blocks
- Server trusts client targeting, validates distance only

## 4. Full Damage Formula

```
1. baseDamage = weapon.getAttackDamage()
2. enchantBonus = getDamageBonus(weapon, target.getMobType())
   // Sharpness: 0.5 + 0.5 * level (Sharp V = 3.0)
   // Smite/Bane: 2.5 * level
3. cooldownMult = 0.2 + (attackStrength^2) * 0.8
4. damage = baseDamage * cooldownMult
5. enchantBonus = enchantBonus * cooldownMult
6. if (crit): damage *= 1.5
7. damage += enchantBonus
8. Armor reduction (based on armor points + toughness)
9. Protection enchant reduction (capped at 80%)
10. Resistance effect: -20% per level
```

### Example: Netherite Sword + Sharp V + Crit
- Base: 8.0 × 1.0 (full charge) = 8.0
- Crit: 8.0 × 1.5 = 12.0
- Enchant: 3.0 × 1.0 = 3.0
- **Total pre-armor: 15.0 damage (7.5 hearts)**

### Strength Effect
+3 damage per level, added to base attack attribute. IS affected by cooldown multiplier and crit multiplier.

## 5. Invulnerability Frames (I-Frames)

- After being hit: `invulnerableTime = 20` (1 second)
- **First 10 ticks (500ms): entity cannot take damage**
- New hit only applies if it deals MORE damage than previous hit within window
- **Hard floor: cannot deal damage faster than every 500ms regardless of cooldown**
- For trigger bot: NO benefit to attacking faster than i-frame window

## 6. Sweep Attack Mechanics

**Trigger conditions (ALL required):**
1. attackStrength > 0.9
2. NOT sprinting
3. ON the ground
4. Minimal movement (low walkDist delta)
5. Holding a sword (axes don't sweep)
6. NOT a critical hit (crits and sweeps are mutually exclusive — crits need air, sweeps need ground)

**Sweep damage:** 1.0 base + Sweeping Edge bonus
- Sweeping Edge III: floor(baseDamage * 0.75)
- Range: 1.0 block radius around primary target

## 7. Trigger Bot Implementation Strategy

### Core Loop
```
every tick:
    target = rayTrace(eyePos, lookDir, 3.0)
    if target is LivingEntity:
        cooldown = getAttackStrengthScale(0.0)
        if cooldown >= threshold:  // 1.0 perfect, 0.9+ for crits
            if critsOnly && canCrit():
                attack(target)
            else if !critsOnly:
                attack(target)
```

### Key Timings
| Parameter | Value |
|-----------|-------|
| Min time between useful attacks (sword) | 625ms (cooldown) |
| Min time between useful attacks (actual) | 500ms (i-frames) |
| Optimal sword attack interval | 650ms (13 ticks, guarantees full charge) |
| Optimal axe interval | 1000ms (20 ticks) |
| Crit threshold | >0.9 attackStrength |

### Anti-Detection
- Add 1-3 tick random delay after cooldown ready
- Vary the cooldown threshold slightly (0.95-1.0)
- Don't attack instantly when target enters crosshair — add small humanized delay

## 8. Advanced Combat Techniques

### Sprint-Crit Combo
Sprint → Jump → Release W (break sprint) → Attack while falling = **crit + sprint knockback from the jump momentum**

### Trade Avoidance
- Attack at max range (2.8-3.0 blocks), then back up during cooldown
- The player who hits first has DPS advantage due to i-frames

### I-Frame Abuse
- Hit for 8 damage → opponent is immune for 500ms
- If you hit for 6 within that window → IGNORED (less than previous)
- Always ensure each hit is at full power

## 9. Weapon Strategy: Sword vs Axe

| Factor | Sword | Axe |
|--------|-------|-----|
| DPS | Higher (sustained) | Lower |
| Burst damage | Lower per hit | Higher per hit |
| Crit damage (netherite) | 12.0 + enchants | 15.0 + enchants |
| Shield disable | No | Yes (5 seconds) |
| Sweep attack | Yes | No |
| Cooldown | 625ms | 1000-1250ms |
| Best for | Sustained fights | Opening hits, shield break |

**Optimal play:** Axe for opener/shield break, sword for sustained DPS.

## 10. Implementation Plan for Our Mod

### Phase 1: Basic TriggerBot (Current skeleton)
- Attack when cooldown >= configurable threshold
- Check crosshair target within reach
- Optional crit-only mode

### Phase 2: Smart Timing
- Track i-frames — never waste a hit during immunity window
- Calculate exact ms until cooldown is full, attack on the tick
- Factor in target distance and closing speed

### Phase 3: Crit Optimizer
- Integrate with JumpReset: jump, wait for fall, attack for crit
- Auto-release sprint before hitting for crit eligibility
- Combine crit + sprint knockback (sprint → jump → release sprint → crit on fall)

### Phase 4: Weapon Switcher
- Auto-switch sword ↔ axe based on context
- Shield detected → axe mode
- Shield disabled → sword mode
- Opening hit → axe for burst
