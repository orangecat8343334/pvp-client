package com.pvpclient.triggerbot;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/**
 * TriggerBot - Automatically attacks when conditions are met.
 *
 * Core logic:
 * 1. Wait for attack cooldown to reach threshold (configurable, default 90%)
 * 2. Check if crosshair is on a valid target within reach
 * 3. Optionally wait for crit conditions (falling, not on ground)
 * 4. Apply humanized delay with jitter
 * 5. Execute attack
 *
 * State machine: IDLE -> WAITING_COOLDOWN -> WAITING_CRIT -> ATTACKING -> IDLE
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
        // Look for a target
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) return;
        if (config.triggerPlayersOnly && !CombatUtil.isPlayer(target)) return;

        currentTarget = target;
        state = State.WAITING_COOLDOWN;
    }

    private void tickWaitingCooldown(MinecraftClient client, ClientPlayerEntity player) {
        // Verify target is still valid
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null || (config.triggerPlayersOnly && !CombatUtil.isPlayer(target))) {
            state = State.IDLE;
            currentTarget = null;
            return;
        }
        currentTarget = target;

        // Check cooldown threshold
        float cooldown = CombatUtil.getAttackCooldown(player);
        if (cooldown >= config.triggerMinCooldown) {
            if (config.triggerCritsOnly) {
                state = State.WAITING_CRIT;
            } else {
                attackTimer.markNow();
                state = State.READY;
            }
        }
    }

    private void tickWaitingCrit(MinecraftClient client, ClientPlayerEntity player) {
        // Re-check target
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) {
            state = State.IDLE;
            return;
        }

        // Wait for crit conditions
        if (CombatUtil.canCrit(player)) {
            attackTimer.markNow();
            state = State.READY;
        }

        // Fallback: if cooldown is resetting, go back to waiting
        if (CombatUtil.getAttackCooldown(player) < config.triggerMinCooldown * 0.5f) {
            state = State.WAITING_COOLDOWN;
        }
    }

    private void tickReady(MinecraftClient client, ClientPlayerEntity player) {
        // Re-check target
        LivingEntity target = CombatUtil.getTargetEntity(client, config.triggerReach);
        if (target == null) {
            state = State.IDLE;
            return;
        }

        // Wait for humanized delay
        if (attackTimer.hasElapsedWithJitter(config.triggerDelayMs, config.triggerRandomMs)) {
            state = State.ATTACKING;
        }
    }

    private void tickAttacking(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget != null && currentTarget.isAlive()) {
            // Execute the attack
            client.interactionManager.attackEntity(player, currentTarget);
            player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            attackCount++;
        }
        state = State.IDLE;
        currentTarget = null;
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
