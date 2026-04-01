package com.pvpclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

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
     * Conditions: falling (velocity Y < 0), not on ground, not in water,
     * not climbing, not blind, not riding.
     */
    public static boolean canCrit(ClientPlayerEntity player) {
        return player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle()
                && !player.isSprinting();
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
