package com.pvpclient.shielddisable;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * ShieldDisableManager - Automatically disables opponent shields with axe.
 *
 * In 1.9+ PVP, axes disable shields for 5 seconds (100 ticks) on hit.
 * This module detects when an opponent is blocking with a shield and
 * automatically performs the sequence:
 *
 * 1. Detect target is blocking (shield up)
 * 2. Switch hotbar to axe slot
 * 3. Wait a tick for server to register slot change
 * 4. Attack target (axe hit disables shield)
 * 5. Switch back to sword
 *
 * State machine: IDLE -> DETECTED_SHIELD -> SWITCHING_TO_AXE -> ATTACKING -> SWITCHING_BACK -> COOLDOWN -> IDLE
 */
public class ShieldDisableManager {
    public enum State {
        IDLE,
        DETECTED_SHIELD,
        SWITCHING_TO_AXE,
        ATTACKING,
        SWITCHING_BACK,
        COOLDOWN
    }

    private final PvpConfig config;
    private boolean enabled;
    private State state = State.IDLE;
    private final TimingUtil actionTimer = new TimingUtil();
    private final TimingUtil cooldownTimer = new TimingUtil();
    private int previousSlot = -1;
    private LivingEntity currentTarget;
    private int disableCount = 0;

    public ShieldDisableManager(PvpConfig config) {
        this.config = config;
        this.enabled = config.shieldDisableEnabled;
    }

    public void toggle() {
        enabled = !enabled;
        config.shieldDisableEnabled = enabled;
        config.save();
        state = State.IDLE;
        currentTarget = null;
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§6[PVP] §fShield Disable: " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) {
            state = State.IDLE;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(client, player);
            case DETECTED_SHIELD -> tickDetected(client, player);
            case SWITCHING_TO_AXE -> tickSwitchingToAxe(client, player);
            case ATTACKING -> tickAttacking(client, player);
            case SWITCHING_BACK -> tickSwitchingBack(client, player);
            case COOLDOWN -> tickCooldown(client, player);
        }
    }

    private void tickIdle(MinecraftClient client, ClientPlayerEntity player) {
        if (!config.shieldAutoDetect) return;

        // Look for a blocking target
        LivingEntity target = CombatUtil.getTargetEntity(client, 3.5f);
        if (target == null) return;

        // Must be a player holding a shield and actively blocking
        if (target instanceof PlayerEntity playerTarget
                && playerTarget.isHolding(Items.SHIELD)
                && playerTarget.isBlocking()) {

            // If shield faces away from us, skip — we can just hit normally
            if (CombatUtil.isShieldFacingAway(playerTarget, player)) return;

            currentTarget = target;
            state = State.DETECTED_SHIELD;
            actionTimer.markNow();
            PvpClientMod.LOGGER.debug("[Shield Disable] Detected shield on target");
        }
    }

    private void tickDetected(MinecraftClient client, ClientPlayerEntity player) {
        // Verify target is still blocking
        if (currentTarget == null || !currentTarget.isAlive() || !CombatUtil.isBlocking(currentTarget)) {
            state = State.IDLE;
            currentTarget = null;
            return;
        }

        if (!config.shieldAutoSwitch) return;

        // Find axe - prefer configured slot, fallback to search
        int axeSlot = config.shieldAxeSlot;
        if (!(player.getInventory().getStack(axeSlot).getItem() instanceof net.minecraft.item.AxeItem)) {
            axeSlot = CombatUtil.findAxeInHotbar(player);
        }

        if (axeSlot == -1) {
            // No axe found, can't disable shield
            state = State.IDLE;
            return;
        }

        // Save current slot and switch to axe
        previousSlot = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = axeSlot;
        state = State.SWITCHING_TO_AXE;
        actionTimer.markNow();
    }

    private void tickSwitchingToAxe(MinecraftClient client, ClientPlayerEntity player) {
        // Wait for switch delay (let server register the slot change)
        if (!actionTimer.hasElapsed(config.shieldSwitchDelayMs)) return;

        // Verify target still valid and in range
        if (currentTarget == null || !currentTarget.isAlive()) {
            revertSlot(player);
            state = State.IDLE;
            return;
        }

        // Check cooldown is ready
        float cooldown = CombatUtil.getAttackCooldown(player);
        if (cooldown >= 0.9f) {
            state = State.ATTACKING;
        }
    }

    private void tickAttacking(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget != null && currentTarget.isAlive()) {
            // Execute axe attack to disable shield
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(Hand.MAIN_HAND);
            disableCount++;
            PvpClientMod.LOGGER.debug("[Shield Disable] Axe hit landed - shield disabled");
        }

        state = State.SWITCHING_BACK;
        actionTimer.markNow();
    }

    private void tickSwitchingBack(MinecraftClient client, ClientPlayerEntity player) {
        // Wait before switching back
        if (!actionTimer.hasElapsed(config.shieldSwitchBackDelayMs)) return;

        revertSlot(player);
        state = State.COOLDOWN;
        cooldownTimer.markNow();
    }

    private void tickCooldown(MinecraftClient client, ClientPlayerEntity player) {
        // Shield is disabled for 5 seconds, don't need to check again immediately
        // Use a 1-second cooldown before re-scanning
        if (cooldownTimer.hasElapsed(1000)) {
            state = State.IDLE;
            currentTarget = null;
        }
    }

    private void revertSlot(ClientPlayerEntity player) {
        if (previousSlot >= 0 && previousSlot <= 8) {
            player.getInventory().selectedSlot = previousSlot;
            previousSlot = -1;
        }
    }

    public boolean isEnabled() { return enabled; }
    public State getState() { return state; }
    public int getDisableCount() { return disableCount; }
    public LivingEntity getCurrentTarget() { return currentTarget; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.shieldDisableEnabled = enabled;
    }
}
