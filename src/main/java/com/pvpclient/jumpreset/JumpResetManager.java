package com.pvpclient.jumpreset;

import com.pvpclient.config.PvpConfig;
import com.pvpclient.util.TimingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/**
 * JumpResetManager - Handles sprint resetting via jump/W-tap/S-tap after hits.
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
    private boolean inputSnapshotCaptured = false;
    private boolean originalForwardKeyPressed = false;
    private boolean originalBackKeyPressed = false;
    private boolean originalPressingForward = false;
    private boolean originalPressingBack = false;
    private float originalMovementForward = 0.0f;

    public JumpResetManager(PvpConfig config) {
        this.config = config;
        this.enabled = config.jumpResetEnabled;
    }

    public void toggle() {
        enabled = !enabled;
        config.jumpResetEnabled = enabled;
        config.save();
        resetState();
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("\u00A76[PVP] \u00A7fJump Reset: " + (enabled ? "\u00A7aON" : "\u00A7cOFF")), true);
    }

    /**
     * Called when the player sends an attack against a living entity.
     */
    public void onAttackEntity(LivingEntity target) {
        if (!enabled || !config.jumpResetAfterHit || state != State.IDLE || target == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) {
            return;
        }
        if (!target.isAlive() || !canStartReset(client, player)) {
            return;
        }

        if (player.distanceTo(target) > config.jumpResetMaxRange) {
            return;
        }

        ResetMethod method = getActiveMethod();
        if ((method == ResetMethod.W_TAP || method == ResetMethod.S_TAP)
                && !client.options.forwardKey.isPressed()
                && player.forwardSpeed <= 0.0f) {
            return;
        }

        if (!player.isSprinting() && !config.jumpResetAutoSprint) {
            return;
        }

        state = State.HIT_LANDED;
        resetTimer.markNow();
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) {
            resetState();
            return;
        }

        if (state != State.IDLE && (client.currentScreen != null || player.isUsingItem())) {
            resetState();
            return;
        }

        if (config.jumpResetAutoSprint
                && state == State.IDLE
                && !player.isUsingItem()
                && client.options.forwardKey.isPressed()) {
            player.setSprinting(true);
        }

        switch (state) {
            case IDLE -> {
            }
            case HIT_LANDED -> tickHitLanded(client, player);
            case RESETTING -> tickResetting(client, player);
            case RECOVERING -> tickRecovering();
        }
    }

    private void tickHitLanded(MinecraftClient client, ClientPlayerEntity player) {
        if (!resetTimer.hasElapsed(config.jumpResetDelayMs)) {
            return;
        }

        switch (getActiveMethod()) {
            case JUMP -> {
                if (!player.isOnGround() || player.isUsingItem()) {
                    resetState();
                    return;
                }

                player.jump();
                player.setSprinting(true);
                resetCount++;
                state = State.RECOVERING;
                recoveryTimer.markNow();
            }
            case W_TAP, S_TAP -> {
                if (!player.isOnGround() || !client.options.forwardKey.isPressed()) {
                    resetState();
                    return;
                }

                captureInputSnapshot(client, player);
                state = State.RESETTING;
                resetTimer.markNow();
            }
        }
    }

    private void tickResetting(MinecraftClient client, ClientPlayerEntity player) {
        switch (getActiveMethod()) {
            case W_TAP -> {
                if (resetTimer.hasElapsed(config.wTapReleaseDurationMs)) {
                    finishReset(client, player);
                }
            }
            case S_TAP -> {
                if (resetTimer.hasElapsed(config.sTapDurationMs)) {
                    finishReset(client, player);
                }
            }
            default -> resetState();
        }
    }

    private void tickRecovering() {
        if (recoveryTimer.hasElapsed(100)) {
            resetState();
        }
    }

    private void finishReset(MinecraftClient client, ClientPlayerEntity player) {
        if (config.jumpResetAutoSprint || client.options.forwardKey.isPressed()) {
            player.setSprinting(true);
        }

        resetCount++;
        state = State.RECOVERING;
        recoveryTimer.markNow();
        clearInputSnapshot();
    }

    private void captureInputSnapshot(MinecraftClient client, ClientPlayerEntity player) {
        originalForwardKeyPressed = client.options.forwardKey.isPressed();
        originalBackKeyPressed = client.options.backKey.isPressed();
        originalPressingForward = player.input.pressingForward;
        originalPressingBack = player.input.pressingBack;
        originalMovementForward = player.input.movementForward;
        inputSnapshotCaptured = true;
    }

    private void clearInputSnapshot() {
        inputSnapshotCaptured = false;
    }

    private void resetState() {
        state = State.IDLE;
        clearInputSnapshot();
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
        if (!enabled) {
            resetState();
        }
    }

    public boolean isManipulatingMovement() {
        return enabled
                && state == State.RESETTING
                && inputSnapshotCaptured
                && (getActiveMethod() == ResetMethod.W_TAP || getActiveMethod() == ResetMethod.S_TAP);
    }

    public void applyMovementOverride(MinecraftClient client, ClientPlayerEntity player) {
        if (!isManipulatingMovement()) {
            return;
        }

        switch (getActiveMethod()) {
            case W_TAP -> {
                client.options.forwardKey.setPressed(false);
                player.input.pressingForward = false;
                if (player.input.movementForward > 0.0f) {
                    player.input.movementForward = 0.0f;
                }
            }
            case S_TAP -> {
                client.options.forwardKey.setPressed(false);
                client.options.backKey.setPressed(true);
                player.input.pressingForward = false;
                player.input.pressingBack = true;
                player.input.movementForward = -1.0f;
            }
            default -> {
            }
        }
    }

    public void restoreMovementOverride(MinecraftClient client, ClientPlayerEntity player) {
        if (!inputSnapshotCaptured) {
            return;
        }

        client.options.forwardKey.setPressed(originalForwardKeyPressed);
        client.options.backKey.setPressed(originalBackKeyPressed);
        player.input.pressingForward = originalPressingForward;
        player.input.pressingBack = originalPressingBack;
        player.input.movementForward = originalMovementForward;
    }

    private boolean canStartReset(MinecraftClient client, ClientPlayerEntity player) {
        if (client.currentScreen != null) return false;
        if (player.isUsingItem()) return false;
        if (player.isBlocking()) return false;
        if (!player.isOnGround()) return false;
        if (player.isTouchingWater()) return false;
        if (player.isClimbing()) return false;
        return true;
    }
}
