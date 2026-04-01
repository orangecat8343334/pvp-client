package com.pvpclient.jumpreset;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/**
 * JumpResetManager - Handles sprint resetting via jump/W-tap/S-tap after hits.
 *
 * Sprint resetting is essential in 1.9+ PVP because:
 * - Sprint hits deal extra knockback (first hit only while sprinting)
 * - After a sprint hit, sprint is broken and subsequent hits deal less KB
 * - Resetting sprint between hits maintains maximum knockback for combos
 *
 * Methods:
 * 1. Jump Reset: Jump immediately after hitting to reset sprint state
 *    - Pros: Reliable, maintains forward momentum
 *    - Cons: Airborne = vulnerable to crits
 *
 * 2. W-Tap: Briefly release W to cancel sprint, re-press to re-sprint
 *    - Pros: Stay grounded, harder to crit
 *    - Cons: Slightly lose momentum
 *
 * 3. S-Tap: Briefly tap S to cancel sprint more aggressively
 *    - Pros: Fastest sprint reset
 *    - Cons: Lose the most momentum
 *
 * State machine: IDLE -> HIT_LANDED -> RESETTING -> RECOVERING -> IDLE
 */
public class JumpResetManager {
    public enum State {
        IDLE,
        HIT_LANDED,
        RESETTING,
        RECOVERING
    }

    public enum ResetMethod {
        JUMP, W_TAP, S_TAP
    }

    private final PvpConfig config;
    private boolean enabled;
    private State state = State.IDLE;
    private final TimingUtil resetTimer = new TimingUtil();
    private final TimingUtil recoveryTimer = new TimingUtil();
    private int resetCount = 0;
    private boolean wasSprintPressed = false;
    private boolean wasForwardPressed = false;

    public JumpResetManager(PvpConfig config) {
        this.config = config;
        this.enabled = config.jumpResetEnabled;
    }

    public void toggle() {
        enabled = !enabled;
        config.jumpResetEnabled = enabled;
        config.save();
        state = State.IDLE;
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§6[PVP] §fJump Reset: " + (enabled ? "§aON" : "§cOFF")), true);
    }

    /**
     * Called when the player successfully lands a hit on an entity.
     * This is triggered from our mixin on attack.
     */
    public void onAttackEntity(LivingEntity target) {
        if (!enabled) return;
        if (state != State.IDLE) return;

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        // Only reset if target is in range for comboing
        double dist = player.distanceTo(target);
        if (dist > config.jumpResetMaxRange) return;

        state = State.HIT_LANDED;
        resetTimer.markNow();
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) {
            state = State.IDLE;
            return;
        }

        // Auto-sprint: keep sprint on
        if (config.jumpResetAutoSprint && player.forwardSpeed > 0) {
            player.setSprinting(true);
        }

        switch (state) {
            case IDLE -> {} // Nothing to do
            case HIT_LANDED -> tickHitLanded(client, player);
            case RESETTING -> tickResetting(client, player);
            case RECOVERING -> tickRecovering(client, player);
        }
    }

    private void tickHitLanded(MinecraftClient client, ClientPlayerEntity player) {
        // Wait for configured delay after hit
        if (!resetTimer.hasElapsed(config.jumpResetDelayMs)) return;

        ResetMethod method = getActiveMethod();
        GameOptions options = client.options;

        switch (method) {
            case JUMP -> {
                // Jump to reset sprint
                if (player.isOnGround()) {
                    player.jump();
                    player.setSprinting(true);
                    resetCount++;
                    state = State.IDLE; // Jump reset is instant
                }
            }
            case W_TAP -> {
                // Release forward key to break sprint
                wasForwardPressed = options.forwardKey.isPressed();
                // We'll handle the key manipulation in the mixin
                state = State.RESETTING;
                resetTimer.markNow();
            }
            case S_TAP -> {
                // Tap backward key to break sprint harder
                state = State.RESETTING;
                resetTimer.markNow();
            }
        }
    }

    private void tickResetting(MinecraftClient client, ClientPlayerEntity player) {
        ResetMethod method = getActiveMethod();

        switch (method) {
            case W_TAP -> {
                if (resetTimer.hasElapsed(config.wTapReleaseDurationMs)) {
                    // Re-press W, re-sprint
                    player.setSprinting(true);
                    resetCount++;
                    state = State.RECOVERING;
                    recoveryTimer.markNow();
                }
            }
            case S_TAP -> {
                if (resetTimer.hasElapsed(config.sTapDurationMs)) {
                    // Release S, re-sprint
                    player.setSprinting(true);
                    resetCount++;
                    state = State.RECOVERING;
                    recoveryTimer.markNow();
                }
            }
            default -> state = State.IDLE;
        }
    }

    private void tickRecovering(MinecraftClient client, ClientPlayerEntity player) {
        // Brief cooldown before allowing another reset
        if (recoveryTimer.hasElapsed(100)) {
            state = State.IDLE;
        }
    }

    private ResetMethod getActiveMethod() {
        if (config.jumpResetUseSTap) return ResetMethod.S_TAP;
        if (config.jumpResetUseWTap) return ResetMethod.W_TAP;
        return ResetMethod.JUMP;
    }

    public boolean isEnabled() { return enabled; }
    public State getState() { return state; }
    public int getResetCount() { return resetCount; }
    public ResetMethod getActiveResetMethod() { return getActiveMethod(); }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.jumpResetEnabled = enabled;
    }

    /**
     * Whether this module needs to suppress the forward key this tick (for W-tap).
     */
    public boolean shouldSuppressForward() {
        return enabled && state == State.RESETTING && getActiveMethod() == ResetMethod.W_TAP;
    }

    /**
     * Whether this module needs to press the back key this tick (for S-tap).
     */
    public boolean shouldPressBack() {
        return enabled && state == State.RESETTING && getActiveMethod() == ResetMethod.S_TAP;
    }
}
