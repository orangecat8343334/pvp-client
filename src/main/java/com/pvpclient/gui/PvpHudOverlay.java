package com.pvpclient.gui;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.triggerbot.TriggerBot;
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
        context.drawText(tr, "§6[PVP Client]", x, y, 0xFFFFFF, true);
        y += lineHeight;

        // TriggerBot status
        TriggerBot tb = PvpClientMod.getTriggerBot();
        if (tb != null) {
            String tbStatus = tb.isEnabled()
                    ? "§aTB §7[" + tb.getState().name() + "] §8Hits:" + tb.getAttackCount()
                    : "§cTB OFF";
            context.drawText(tr, tbStatus, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // JumpReset status
        JumpResetManager jr = PvpClientMod.getJumpResetManager();
        if (jr != null) {
            String jrStatus = jr.isEnabled()
                    ? "§aJR §7[" + jr.getActiveResetMethod().name() + "] §8Resets:" + jr.getResetCount()
                    : "§cJR OFF";
            context.drawText(tr, jrStatus, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // ShieldDisable status
        ShieldDisableManager sd = PvpClientMod.getShieldDisableManager();
        if (sd != null) {
            String sdStatus = sd.isEnabled()
                    ? "§aSD §7[" + sd.getState().name() + "] §8Disables:" + sd.getDisableCount()
                    : "§cSD OFF";
            context.drawText(tr, sdStatus, x, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        // Attack cooldown bar
        float cooldown = CombatUtil.getAttackCooldown(client.player);
        int barWidth = 60;
        int filled = (int) (cooldown * barWidth);
        String cdColor = cooldown >= 0.9f ? "§a" : cooldown >= 0.5f ? "§e" : "§c";
        context.drawText(tr, cdColor + String.format("CD: %.0f%%", cooldown * 100), x, y, 0xFFFFFF, true);
    }
}
