package com.pvpclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class CombatUtil {

    /**
     * Get the attack cooldown progress (0.0 to 1.0).
     * 1.0 = fully charged, ready to attack.
     */
    public static float getAttackCooldown(ClientPlayerEntity player) {
        return player.getAttackCooldownProgress(0.5f);
    }

    /**
     * Check if the player is looking at a living entity within reach.
     */
    public static LivingEntity getTargetEntity(MinecraftClient client, float maxReach) {
        if (client.crosshairTarget != null
                && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
            if (entity instanceof LivingEntity living) {
                double dist = client.player.squaredDistanceTo(entity);
                if (dist <= maxReach * maxReach) {
                    return living;
                }
            }
        }
        return null;
    }

    /**
     * Check if the player can land a critical hit right now.
     * Matches vanilla conditions from PlayerEntity.attack():
     * fallDistance > 0, not on ground, not climbing, not in water,
     * no blindness, not riding, not sprinting, cooldown > 0.9
     */
    public static boolean canCrit(ClientPlayerEntity player) {
        return player.getAttackCooldownProgress(0.5f) > 0.9f
                && player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isSubmergedInWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting();
    }

    /**
     * Check if a player's shield is facing away from us (we can hit from behind).
     * Uses dot product: target's facing direction vs direction from target to us.
     * If dot < 0, shield faces away — we can bypass it without axe.
     * Ported from argon's WorldUtils.isShieldFacingAway().
     */
    public static boolean isShieldFacingAway(PlayerEntity target, ClientPlayerEntity attacker) {
        Vec3d dirToAttacker = attacker.getPos().subtract(target.getPos()).normalize();

        float yaw = target.getYaw();
        float pitch = target.getPitch();
        Vec3d facing = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).normalize();

        return facing.dotProduct(dirToAttacker) < 0;
    }

    /**
     * Check if player is ascending (just jumped, Y velocity > 0).
     * Useful to avoid attacking while rising (wastes crit opportunity).
     */
    public static boolean isAscending(ClientPlayerEntity player) {
        return !player.isOnGround() && player.getVelocity().y > 0;
    }

    /**
     * Check if a living entity is actively blocking with a shield.
     */
    public static boolean isBlocking(LivingEntity entity) {
        return entity.isBlocking();
    }

    /**
     * Check if the player is holding a sword in main hand.
     */
    public static boolean isHoldingSword(ClientPlayerEntity player) {
        Item item = player.getMainHandStack().getItem();
        return item instanceof SwordItem;
    }

    /**
     * Check if the player is holding an axe in main hand.
     */
    public static boolean isHoldingAxe(ClientPlayerEntity player) {
        Item item = player.getMainHandStack().getItem();
        return item instanceof AxeItem;
    }

    /**
     * Find an axe in the player's hotbar. Returns slot index (0-8) or -1 if not found.
     */
    public static int findAxeInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find a sword in the player's hotbar. Returns slot index (0-8) or -1 if not found.
     */
    public static int findSwordInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof SwordItem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if target is a player (vs mob).
     */
    public static boolean isPlayer(Entity entity) {
        return entity instanceof PlayerEntity;
    }
}
