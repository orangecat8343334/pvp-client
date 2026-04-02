package com.pvpclient.triggerbot;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.shielddisable.ShieldDisableManager;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TriggerBot - Automatically attacks when conditions are met.
 *
 * State machine: IDLE -> WAITING_COOLDOWN -> WAITING_CRIT -> READY -> ATTACKING
 */
public class TriggerBot {
    public enum State {
        IDLE,
        WAITING_COOLDOWN,
        WAITING_CRIT,
        READY,
        ATTACKING
    }

    private final PvpConfig config;
    private boolean enabled;
    private State state = State.IDLE;
    private final TimingUtil attackTimer = new TimingUtil();
    private LivingEntity currentTarget;
    private int attackCount = 0;
    private int currentDelay = 0;

    public TriggerBot(PvpConfig config) {
        this.config = config;
        this.enabled = config.triggerBotEnabled;
    }

    public void toggle() {
        enabled = !enabled;
        config.triggerBotEnabled = enabled;
        config.save();
        resetState();
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("\u00A76[PVP] \u00A7fTriggerBot: " + (enabled ? "\u00A7aON" : "\u00A7cOFF")), true);
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null
                || player.isDead()
                || player.isUsingItem()
                || client.interactionManager == null
                || client.currentScreen != null) {
            resetState();
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(client, player);
            case WAITING_COOLDOWN -> tickWaitingCooldown(client, player);
            case WAITING_CRIT -> tickWaitingCrit(client, player);
            case READY -> tickReady(client, player);
            case ATTACKING -> tickAttacking(client, player);
        }
    }

    private void tickIdle(MinecraftClient client, ClientPlayerEntity player) {
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null || !target.isAlive()) {
            return;
        }
        if (config.triggerPlayersOnly && !CombatUtil.isPlayer(target)) {
            return;
        }
        if (shouldYieldToShieldDisable(client, target)) {
            return;
        }

        currentTarget = target;
        currentDelay = getWeaponDelay(player);
        state = State.WAITING_COOLDOWN;
    }

    private void tickWaitingCooldown(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = refreshCurrentTarget(client);
        if (target == null) {
            return;
        }
        if (shouldYieldToShieldDisable(client, target)) {
            resetState();
            return;
        }

        if (config.triggerAntiAscend && CombatUtil.isAscending(player)) {
            return;
        }

        if (!attackTimer.hasElapsed(currentDelay)) {
            return;
        }

        if (CombatUtil.getAttackCooldown(player) >= config.triggerMinCooldown) {
            if (shouldWaitForCrit(player)) {
                state = State.WAITING_CRIT;
            } else {
                state = State.READY;
            }
        }
    }

    private void tickWaitingCrit(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = refreshCurrentTarget(client);
        if (target == null) {
            return;
        }
        if (shouldYieldToShieldDisable(client, target)) {
            resetState();
            return;
        }

        if (!attackTimer.hasElapsed(currentDelay)) {
            return;
        }

        if (CombatUtil.canCrit(player)) {
            state = State.READY;
        }

        if (CombatUtil.getAttackCooldown(player) < config.triggerMinCooldown * 0.5f) {
            state = State.WAITING_COOLDOWN;
        }
    }

    private void tickReady(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = refreshCurrentTarget(client);
        if (target == null) {
            return;
        }
        if (shouldYieldToShieldDisable(client, target)) {
            resetState();
            return;
        }

        state = State.ATTACKING;
    }

    private void tickAttacking(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget != null
                && currentTarget.isAlive()
                && CombatUtil.isTargetUnderCrosshair(client, currentTarget, config.triggerReach)
                && !shouldYieldToShieldDisable(client, currentTarget)) {
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(Hand.MAIN_HAND);
            attackTimer.markNow();
            currentDelay = getWeaponDelay(player);
            attackCount++;
        }

        resetState();
    }

    private int getWeaponDelay(ClientPlayerEntity player) {
        if (CombatUtil.isHoldingAxe(player)) {
            return randomInRange(config.triggerAxeDelayMin, config.triggerAxeDelayMax);
        }
        return randomInRange(config.triggerSwordDelayMin, config.triggerSwordDelayMax);
    }

    private boolean shouldWaitForCrit(ClientPlayerEntity player) {
        if (CombatUtil.isHoldingAxe(player)) {
            return config.triggerCritAxe;
        }
        if (CombatUtil.isHoldingSword(player)) {
            return config.triggerCritSword;
        }
        return config.triggerCritsOnly;
    }

    private LivingEntity refreshCurrentTarget(MinecraftClient client) {
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null || !target.isAlive() || (config.triggerPlayersOnly && !CombatUtil.isPlayer(target))) {
            resetState();
            return null;
        }

        currentTarget = target;
        return target;
    }

    private boolean shouldYieldToShieldDisable(MinecraftClient client, LivingEntity target) {
        if (!config.triggerCheckShield || !(target instanceof PlayerEntity playerTarget) || client.player == null) {
            return false;
        }

        ShieldDisableManager shieldDisableManager = PvpClientMod.getShieldDisableManager();
        if (shieldDisableManager != null
                && shieldDisableManager.isEnabled()
                && shieldDisableManager.shouldTakePriority(client, target)) {
            return true;
        }

        return CombatUtil.isHoldingSword(client.player)
                && CombatUtil.isShieldBlockingUs(playerTarget, client.player);
    }

    private void resetState() {
        state = State.IDLE;
        currentTarget = null;
    }

    private static int randomInRange(int min, int max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public boolean isEnabled() { return enabled; }
    public State getState() { return state; }
    public LivingEntity getCurrentTarget() { return currentTarget; }
    public int getAttackCount() { return attackCount; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.triggerBotEnabled = enabled;
    }
}
