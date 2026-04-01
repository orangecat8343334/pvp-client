# Shield Disable Mechanics - Deep Research

## 1. Shield Basics
- Activated by holding use (right-click) with shield in either hand
- **5-tick (250ms) warmup** before shield is active — exploitable window
- Blocks ALL melee and projectile damage from front 180-degree arc
- Durability takes damage = attack damage + 1

## 2. What Shields Block vs Bypass

**Blocked:** All melee from front, arrows, tridents, fireballs, explosions (damage not KB)

**Bypasses shields:**
- Status effect damage (poison, wither, instant damage as applied effects)
- Piercing enchant on crossbow
- Attacks from behind (outside 180-degree arc)
- Warden sonic boom
- Fire/lava tick damage, fall, suffocation, drowning, void

## 3. Blocking Angle Check
- 180-degree hemisphere in front of player
- Code: dot product between player facing direction and vector to damage source
- If dot product < 0 (attack from behind), shield does NOT block
- **Flanking is a viable strategy against shield users**

## 4. Axe Shield Disable — The Core Mechanic
- Any axe hit on a blocking player **disables shield for 100 ticks (5 seconds)**
- Player cannot raise ANY shield during cooldown
- **Conditions:**
  - Attacker uses an AXE (any material)
  - Attack cooldown must be >= 90% (`getAttackStrengthScale >= 0.9`)
  - Does NOT require sprinting
  - Target must be actively blocking
  - Attack must hit from within shield's 180-degree arc
- **Critical nuance:** The shield blocks the damage from the hit that disables it. You must follow up with a second attack to deal damage.

## 5. Attack Cooldown Per Weapon
| Weapon | Attack Speed | Cooldown | Ticks for 90%+ |
|--------|-------------|----------|-----------------|
| Sword | 1.6/sec | 625ms | ~11 ticks |
| Diamond/Netherite Axe | 1.0/sec | 1000ms | ~18 ticks |
| Iron Axe | 0.9/sec | ~1110ms | ~20 ticks |
| Stone/Wood/Gold Axe | 0.8/sec | 1250ms | ~22 ticks |

**KEY INSIGHT:** Cooldown is tracked on the PLAYER, not per-weapon. Formula uses `ticksSinceLastAttack / currentWeaponCooldownPeriod`. If you haven't attacked recently, switching to axe and attacking immediately shows full cooldown because the numerator is large.

## 6. Detecting Shield State (Client-Side)
```java
// Simple check
boolean blocking = target.isBlocking();

// Detailed check
boolean isUsingItem = target.isUsingItem();
ItemStack activeItem = target.getActiveItem();
boolean hasShield = activeItem.getItem() instanceof ShieldItem;
int useTime = target.getItemUseTime();
boolean warmupComplete = useTime >= 5; // 5-tick warmup
```

**Packet-level:** `ClientboundSetEntityDataPacket` contains living entity flags byte with `isUsingItem` bit.

## 7. The Optimal Auto-Disable Sequence

### Packets involved:
1. `ServerboundSetCarriedItemPacket(axeSlot)` — switch to axe
2. `ServerboundInteractPacket(ATTACK, targetId)` — attack
3. `ServerboundSwingPacket` — arm swing
4. `ServerboundSetCarriedItemPacket(originalSlot)` — switch back

### Can all happen in 1 server tick (50ms)!

### Implementation:
```java
float cooldown = mc.player.getAttackCooldownProgress(0.5f);
if (cooldown >= 0.9f && axeSlot != -1) {
    int originalSlot = inv.selectedSlot;

    // Switch to axe
    inv.selectedSlot = axeSlot;
    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));

    // Attack
    mc.interactionManager.attackEntity(mc.player, targetPlayer);

    // Switch back
    inv.selectedSlot = originalSlot;
    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
}
```

### Conservative approach (anti-cheat safe):
- Tick N: Switch to axe
- Tick N+1: Attack
- Tick N+2: Switch back
- Adds 100-150ms but looks more natural

## 8. Follow-up Window After Disable
- Shield disabled for 100 ticks (5000ms)
- Entity invulnerability frames: 10 ticks (500ms)
- **Effective follow-up window: ~90 ticks (4500ms)**
- Sword cooldown: 12.5 ticks (625ms)
- Can get ~7 sword hits in the disable window

## 9. Alternative Shield Counter Strategies
| Method | Mechanic | Notes |
|--------|----------|-------|
| Axe disable | 5s cooldown | Primary method |
| Piercing crossbow | Arrows bypass shield | Piercing enchant 1+ |
| Flanking | Attack from behind | Outside 180-degree arc |
| Lava bucket | Fire tick bypasses | Ongoing fire damage |
| Status effects | Applied effects bypass | Poison/wither |

## 10. Advanced Shield Techniques to Counter
- **Drop-shield-bait:** Lower shield to bait attack, re-raise (risky: 250ms warmup gap)
- **Shield-sword combo:** Block, wait for axe swap commitment, drop + hit during their slow axe cooldown
- **Shield critting:** Can't crit while blocking. Must drop shield → jump-attack → re-raise

## 11. Key Timing Summary
| Action | Duration |
|--------|----------|
| Shield warmup | 5 ticks (250ms) |
| Shield disable duration | 100 ticks (5000ms) |
| Diamond axe cooldown | 20 ticks (1000ms) |
| Sword cooldown | 12.5 ticks (625ms) |
| Slot switch | 0 ticks (instant) |
| Full swap sequence | 1 tick (50ms) |
| Entity i-frames | 10 ticks (500ms) |
| Follow-up sword window | 90 ticks (4500ms) |

## 12. Implementation Plan for Our Mod

### Phase 1: Basic Shield Disable (Current skeleton)
- Detect `isBlocking()` on crosshair target
- Switch to axe → attack → switch back with configurable delays
- Works but spread across multiple ticks for safety

### Phase 2: Packet-Level Optimization
- Send slot-change + attack + slot-change packets in same tick
- Add packet-level shield detection via entity metadata mixin
- Track shield warmup timing for exploit window

### Phase 3: Advanced Integration
- Combine with TriggerBot (don't trigger sword hit if target is shielding — trigger axe disable instead)
- Predict shield raises based on opponent patterns
- Auto-follow-up combo after disable (sword attacks during 5s window)
- Strafe to exploit shield angle (attack from side/behind)
