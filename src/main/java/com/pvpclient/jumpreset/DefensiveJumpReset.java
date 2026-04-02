package com.pvpclient.jumpreset;

import com.pvpclient.config.PvpConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.ThreadLocalRandom;

/**
 * DefensiveJumpReset - Jump when YOU get hit to reduce knockback taken.
 *
 * Ported from argon's AutoJumpReset. When hit (hurtTime > 0), jump on a
 * specific hurt tick to reduce incoming knockback. Jumping at hurtTime == 9
 * (1 tick after max hurt) applies upward velocity that partially counters
 * the knockback velocity, reducing how far you're pushed.
 *
 * This is separate from the offensive JumpResetManager which resets YOUR
 * sprint after YOU hit someone.
 */
public class DefensiveJumpReset {
    private final PvpConfig config;
    private boolean enabled;
    private int jumpCount = 0;

    public DefensiveJumpReset(PvpConfig config) {
        this.config = config;
        this.enabled = config.defensiveJumpResetEnabled;
    }

    public void toggle() {
        enabled = !enabled;
        config.defensiveJumpResetEnabled = enabled;
        config.save();
        MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§6[PVP] §fKB Reduce: " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isDead()) return;
        if (client.currentScreen != null) return;
        if (player.isUsingItem()) return;

        // Chance check
        if (ThreadLocalRandom.current().nextInt(100) >= config.defensiveJumpChance) return;

        // hurtTime counts down from maxHurtTime (10) to 0
        // Skip if not hurt or at max hurt time (first tick of being hit)
        if (player.hurtTime == 0) return;
        if (player.hurtTime == player.maxHurtTime) return;
        if (!player.isOnGround()) return;

        // Jump at hurtTime == 9 (1 tick after hit registered)
        // This is the sweet spot from argon's implementation
        if (player.hurtTime == 9) {
            player.jump();
            jumpCount++;
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getJumpCount() { return jumpCount; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.defensiveJumpResetEnabled = enabled;
    }
}
