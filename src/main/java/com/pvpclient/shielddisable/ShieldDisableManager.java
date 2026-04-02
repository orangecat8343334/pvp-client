package com.pvpclient.shielddisable;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.mixin.ClientPlayerInteractionManagerAccessor;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * ShieldDisableManager - Automatically disables opponent shields with axe.
 *
 * State machine: IDLE -> DETECTED_SHIELD -> SWITCHING_TO_AXE -> ATTACKING -> SWITCHING_BACK -> COOLDOWN
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
        resetState();
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("\u00A76[PVP] \u00A7fShield Disable: " + (enabled ? "\u00A7aON" : "\u00A7cOFF")), true);
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null
                || player.isDead()
                || player.isUsingItem()
                || client.interactionManager == null
                || client.currentScreen != null) {
            if (player != null) {
                revertSlot(client, player);
            }
            resetState();
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(client, player);
            case DETECTED_SHIELD -> tickDetected(client, player);
            case SWITCHING_TO_AXE -> tickSwitchingToAxe(client, player);
            case ATTACKING -> tickAttacking(client, player);
            case SWITCHING_BACK -> tickSwitchingBack(client, player);
            case COOLDOWN -> tickCooldown();
        }
    }

    private void tickIdle(MinecraftClient client, ClientPlayerEntity player) {
        if (!config.shieldAutoDetect) {
            return;
        }

        LivingEntity target = getShieldTarget(client, player);
        if (target == null) {
            return;
        }

        currentTarget = target;
        state = State.DETECTED_SHIELD;
        actionTimer.markNow();
    }

    private void tickDetected(MinecraftClient client, ClientPlayerEntity player) {
        if (!isStillValidTarget(client, player, currentTarget)) {
            resetState();
            return;
        }

        if (!config.shieldAutoSwitch) {
            if (CombatUtil.isHoldingAxe(player)) {
                state = State.ATTACKING;
            } else {
                resetState();
            }
            return;
        }

        int axeSlot = findAxeSlot(player);
        if (axeSlot == -1) {
            resetState();
            return;
        }

        previousSlot = player.getInventory().selectedSlot;
        if (previousSlot != axeSlot) {
            selectHotbarSlot(client, player, axeSlot);
        }

        state = State.SWITCHING_TO_AXE;
        actionTimer.markNow();
    }

    private void tickSwitchingToAxe(MinecraftClient client, ClientPlayerEntity player) {
        if (!isStillValidTarget(client, player, currentTarget)) {
            revertSlot(client, player);
            resetState();
            return;
        }

        if (!actionTimer.hasElapsed(config.shieldSwitchDelayMs)) {
            return;
        }

        if (CombatUtil.getAttackCooldown(player) >= 0.9f) {
            state = State.ATTACKING;
        }
    }

    private void tickAttacking(MinecraftClient client, ClientPlayerEntity player) {
        if (isStillValidTarget(client, player, currentTarget)) {
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(Hand.MAIN_HAND);
            disableCount++;
        }

        currentTarget = null;
        if (previousSlot != -1 && previousSlot != player.getInventory().selectedSlot) {
            state = State.SWITCHING_BACK;
            actionTimer.markNow();
            return;
        }

        state = State.COOLDOWN;
        cooldownTimer.markNow();
    }

    private void tickSwitchingBack(MinecraftClient client, ClientPlayerEntity player) {
        if (!actionTimer.hasElapsed(config.shieldSwitchBackDelayMs)) {
            return;
        }

        revertSlot(client, player);
        currentTarget = null;
        state = State.COOLDOWN;
        cooldownTimer.markNow();
    }

    private void tickCooldown() {
        if (cooldownTimer.hasElapsed(1000)) {
            resetState();
        }
    }

    public boolean shouldTakePriority(MinecraftClient client, LivingEntity target) {
        ClientPlayerEntity player = client.player;
        if (!enabled || !config.shieldAutoDetect || player == null || target == null) {
            return false;
        }

        if (state == State.COOLDOWN) {
            return false;
        }

        if (state != State.IDLE) {
            return target == currentTarget || canHandleTarget(player, target);
        }

        return canHandleTarget(player, target);
    }

    private boolean canHandleTarget(ClientPlayerEntity player, LivingEntity target) {
        if (!isShieldTarget(player, target)) {
            return false;
        }

        return CombatUtil.isHoldingAxe(player) || findAxeSlot(player) != -1;
    }

    private LivingEntity getShieldTarget(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = CombatUtil.getTargetEntity(client, getDetectionReach());
        if (target == null || !CombatUtil.isTargetUnderCrosshair(client, target, getDetectionReach())) {
            return null;
        }

        return isShieldTarget(player, target) ? target : null;
    }

    private boolean isStillValidTarget(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
        return target != null
                && target.isAlive()
                && CombatUtil.isTargetUnderCrosshair(client, target, getDetectionReach())
                && isShieldTarget(player, target);
    }

    private boolean isShieldTarget(ClientPlayerEntity player, LivingEntity target) {
        if (!(target instanceof PlayerEntity playerTarget)) {
            return false;
        }

        return playerTarget.isHolding(Items.SHIELD)
                && CombatUtil.isShieldBlockingUs(playerTarget, player);
    }

    private int findAxeSlot(ClientPlayerEntity player) {
        int configuredSlot = config.shieldAxeSlot;
        if (configuredSlot >= 0
                && configuredSlot <= 8
                && player.getInventory().getStack(configuredSlot).getItem() instanceof AxeItem) {
            return configuredSlot;
        }

        return CombatUtil.findAxeInHotbar(player);
    }

    private float getDetectionReach() {
        return Math.max(3.0f, config.triggerReach);
    }

    private void selectHotbarSlot(MinecraftClient client, ClientPlayerEntity player, int slot) {
        player.getInventory().selectedSlot = slot;
        ((ClientPlayerInteractionManagerAccessor) client.interactionManager).pvpClient$syncSelectedSlot();
    }

    private void revertSlot(MinecraftClient client, ClientPlayerEntity player) {
        if (previousSlot < 0 || previousSlot > 8) {
            previousSlot = -1;
            return;
        }

        if (player.getInventory().selectedSlot != previousSlot) {
            selectHotbarSlot(client, player, previousSlot);
        }

        previousSlot = -1;
    }

    private void resetState() {
        state = State.IDLE;
        currentTarget = null;
        previousSlot = -1;
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
