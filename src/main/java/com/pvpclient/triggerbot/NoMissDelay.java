package com.pvpclient.triggerbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.HitResult;

/**
 * NoMissDelay - Prevents wasted attacks on air/blocks that reset cooldown.
 *
 * Ported from argon's NoMissDelay. In vanilla, clicking on air or blocks
 * still triggers the attack cooldown, penalizing your next real hit.
 * This intercepts the attack and cancels it if crosshair isn't on an entity.
 *
 * Used as a utility check, not a standalone module with state.
 */
public class NoMissDelay {

    /**
     * Returns true if this attack should be CANCELLED to preserve cooldown.
     * Call this before processing any attack input.
     */
    public static boolean shouldCancelAttack(MinecraftClient client, boolean onlyWeapons, boolean cancelAir, boolean cancelBlocks) {
        if (client.player == null) return false;

        // Only apply to weapons if configured
        if (onlyWeapons) {
            var item = client.player.getMainHandStack().getItem();
            if (!(item instanceof SwordItem || item instanceof AxeItem)) {
                return false;
            }
        }

        if (client.crosshairTarget == null) return cancelAir;

        return switch (client.crosshairTarget.getType()) {
            case MISS -> cancelAir;
            case BLOCK -> cancelBlocks;
            case ENTITY -> false; // Always allow entity hits
        };
    }
}
