package com.pvpclient.triggerbot;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TriggerBot - Automatically attacks when conditions are met.
 *
 * Inspired by argon's TriggerBot with improvements:
 * - Separate sword/axe delays with min-max random ranges
 * - Shield facing check (don't waste axe disable if shield faces away)
 * - No-miss: skip attacks on air/blocks to preserve cooldown
 * - Crit-only mode per weapon type
 * - Anti-ascend: don't attack while rising (wastes crit window)
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
        state = State.IDLE;
        currentTarget = null;
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§6[PVP] §fTriggerBot: " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) {
            state = State.IDLE;
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
        // No-miss: only proceed if crosshair is on an entity
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) return;
        if (config.triggerPlayersOnly && !CombatUtil.isPlayer(target)) return;

        // Check if target is shielding and we're in front — let ShieldDisable handle it
        if (target instanceof PlayerEntity playerTarget && playerTarget.isBlocking()) {
            if (!CombatUtil.isShieldFacingAway(playerTarget, player)) {
                // Shield is facing us — skip if we're holding a sword (ShieldDisable will handle axe swap)
                if (CombatUtil.isHoldingSword(player) && config.triggerCheckShield) {
                    return;
                }
            }
        }

        currentTarget = target;
        // Randomize delay based on weapon type (like argon's MinMaxSetting)
        currentDelay = getWeaponDelay(player);
        state = State.WAITING_COOLDOWN;
    }

    private void tickWaitingCooldown(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null || (config.triggerPlayersOnly && !CombatUtil.isPlayer(target))) {
            state = State.IDLE;
            currentTarget = null;
            return;
        }
        currentTarget = target;

        // Don't attack while ascending (wastes crit opportunity)
        if (config.triggerAntiAscend && CombatUtil.isAscending(player)) return;

        float cooldown = CombatUtil.getAttackCooldown(player);
        if (cooldown >= config.triggerMinCooldown) {
            if (shouldWaitForCrit(player)) {
                state = State.WAITING_CRIT;
            } else {
                attackTimer.markNow();
                state = State.READY;
            }
        }
    }

    private void tickWaitingCrit(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) {
            state = State.IDLE;
            return;
        }

        if (CombatUtil.canCrit(player)) {
            attackTimer.markNow();
            state = State.READY;
        }

        // Fallback: if cooldown is resetting, go back
        if (CombatUtil.getAttackCooldown(player) < config.triggerMinCooldown * 0.5f) {
            state = State.WAITING_COOLDOWN;
        }
    }

    private void tickReady(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) {
            state = State.IDLE;
            return;
        }

        if (attackTimer.hasElapsed(currentDelay)) {
            state = State.ATTACKING;
        }
    }

    private void tickAttacking(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget != null && currentTarget.isAlive()) {
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(Hand.MAIN_HAND);
            attackCount++;
        }
        state = State.IDLE;
        currentTarget = null;
    }

    /**
     * Get randomized delay based on held weapon type.
     * Sword: faster cooldown, shorter delay. Axe: slower cooldown, longer delay.
     */
    private int getWeaponDelay(ClientPlayerEntity player) {
        if (CombatUtil.isHoldingAxe(player)) {
            return randomInRange(config.triggerAxeDelayMin, config.triggerAxeDelayMax);
        }
        return randomInRange(config.triggerSwordDelayMin, config.triggerSwordDelayMax);
    }

    private boolean shouldWaitForCrit(ClientPlayerEntity player) {
        if (CombatUtil.isHoldingAxe(player)) return config.triggerCritAxe;
        if (CombatUtil.isHoldingSword(player)) return config.triggerCritSword;
        return config.triggerCritsOnly;
    }

    private static int randomInRange(int min, int max) {
        if (min >= max) return min;
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
