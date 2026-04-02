package com.pvpclient.gui;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.triggerbot.TriggerBot;
import com.pvpclient.jumpreset.DefensiveJumpReset;
import com.pvpclient.jumpreset.JumpResetManager;
import com.pvpclient.shielddisable.ShieldDisableManager;
import com.pvpclient.util.CombatUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class PvpHudOverlay implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        PvpConfig config = PvpClientMod.getConfig();
        if (client.player == null || config == null || !config.hudEnabled) return;

        TextRenderer tr = client.textRenderer;
        int x = config.hudX;
        int y = config.hudY;
        int lineHeight = 10;

        // Title
        context.drawText(tr, "\u00A76[\u00A7ePVP Client\u00A76]", x, y, 0xFFFFFF, true);
        y += lineHeight;

        // TriggerBot
        TriggerBot tb = PvpClientMod.getTriggerBot();
        if (tb != null) {
            String status = tb.isEnabled()
                    ? "\u00A7aTB \u00A77" + tb.getState().name() + " \u00A78" + tb.getAttackCount()
                    : "\u00A78TB";
            context.drawText(tr, status, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // JumpReset (offensive)
        JumpResetManager jr = PvpClientMod.getJumpResetManager();
        if (jr != null) {
            String status = jr.isEnabled()
                    ? "\u00A7aJR \u00A77" + jr.getActiveResetMethod().name()
                      + (jr.getState() != JumpResetManager.State.IDLE ? " \u00A7e" + jr.getState().name() : "")
                      + " \u00A78" + jr.getResetCount()
                    : "\u00A78JR";
            context.drawText(tr, status, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // ShieldDisable
        ShieldDisableManager sd = PvpClientMod.getShieldDisableManager();
        if (sd != null) {
            String status = sd.isEnabled()
                    ? "\u00A7aSD"
                      + (sd.getState() != ShieldDisableManager.State.IDLE ? " \u00A7e" + sd.getState().name() : "")
                      + " \u00A78" + sd.getDisableCount()
                    : "\u00A78SD";
            context.drawText(tr, status, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // Defensive Jump Reset (KB reduce)
        DefensiveJumpReset djr = PvpClientMod.getDefensiveJumpReset();
        if (djr != null) {
            String status = djr.isEnabled()
                    ? "\u00A7aKB \u00A78" + djr.getJumpCount()
                    : "\u00A78KB";
            context.drawText(tr, status, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // NoMissDelay
        if (config.noMissDelayEnabled) {
            context.drawText(tr, "\u00A7aNM", x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // Cooldown bar
        float cooldown = CombatUtil.getAttackCooldown(client.player);
        String cdColor = cooldown >= 0.9f ? "\u00A7a" : cooldown >= 0.5f ? "\u00A7e" : "\u00A7c";
        context.drawText(tr, cdColor + String.format("%.0f%%", cooldown * 100), x, y, 0xFFFFFF, true);
    }
}
