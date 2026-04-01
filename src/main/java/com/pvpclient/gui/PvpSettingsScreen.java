package com.pvpclient.gui;

import com.pvpclient.config.PvpConfig;
import com.pvpclient.triggerbot.TriggerBot;
import com.pvpclient.jumpreset.JumpResetManager;
import com.pvpclient.shielddisable.ShieldDisableManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * In-game settings screen for PVP Client.
 * Opened with the settings keybind (default: ;)
 */
public class PvpSettingsScreen extends Screen {
    private final Screen parent;
    private final PvpConfig config;
    private final TriggerBot triggerBot;
    private final JumpResetManager jumpResetManager;
    private final ShieldDisableManager shieldDisableManager;

    public PvpSettingsScreen(Screen parent, PvpConfig config,
                              TriggerBot triggerBot, JumpResetManager jumpResetManager,
                              ShieldDisableManager shieldDisableManager) {
        super(Text.literal("PVP Client Settings"));
        this.parent = parent;
        this.config = config;
        this.triggerBot = triggerBot;
        this.jumpResetManager = jumpResetManager;
        this.shieldDisableManager = shieldDisableManager;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        // === TriggerBot Section ===
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TriggerBot: " + (triggerBot.isEnabled() ? "§aON" : "§cOFF")),
                btn -> {
                    triggerBot.toggle();
                    btn.setMessage(Text.literal("TriggerBot: " + (triggerBot.isEnabled() ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Crits Only: " + (config.triggerCritsOnly ? "§aON" : "§cOFF")),
                btn -> {
                    config.triggerCritsOnly = !config.triggerCritsOnly;
                    config.save();
                    btn.setMessage(Text.literal("Crits Only: " + (config.triggerCritsOnly ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Players Only: " + (config.triggerPlayersOnly ? "§aON" : "§cOFF")),
                btn -> {
                    config.triggerPlayersOnly = !config.triggerPlayersOnly;
                    config.save();
                    btn.setMessage(Text.literal("Players Only: " + (config.triggerPlayersOnly ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing + 8;

        // === Jump Reset Section ===
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Jump Reset: " + (jumpResetManager.isEnabled() ? "§aON" : "§cOFF")),
                btn -> {
                    jumpResetManager.toggle();
                    btn.setMessage(Text.literal("Jump Reset: " + (jumpResetManager.isEnabled() ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Method: " + getResetMethodName()),
                btn -> {
                    cycleResetMethod();
                    btn.setMessage(Text.literal("Method: " + getResetMethodName()));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto Sprint: " + (config.jumpResetAutoSprint ? "§aON" : "§cOFF")),
                btn -> {
                    config.jumpResetAutoSprint = !config.jumpResetAutoSprint;
                    config.save();
                    btn.setMessage(Text.literal("Auto Sprint: " + (config.jumpResetAutoSprint ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing + 8;

        // === Shield Disable Section ===
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Shield Disable: " + (shieldDisableManager.isEnabled() ? "§aON" : "§cOFF")),
                btn -> {
                    shieldDisableManager.toggle();
                    btn.setMessage(Text.literal("Shield Disable: " + (shieldDisableManager.isEnabled() ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto Switch: " + (config.shieldAutoSwitch ? "§aON" : "§cOFF")),
                btn -> {
                    config.shieldAutoSwitch = !config.shieldAutoSwitch;
                    config.save();
                    btn.setMessage(Text.literal("Auto Switch: " + (config.shieldAutoSwitch ? "§aON" : "§cOFF")));
                }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing + 16;

        // Done button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> close()
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
    }

    private String getResetMethodName() {
        if (config.jumpResetUseSTap) return "§eS-Tap";
        if (config.jumpResetUseWTap) return "§eW-Tap";
        return "§eJump";
    }

    private void cycleResetMethod() {
        if (!config.jumpResetUseWTap && !config.jumpResetUseSTap) {
            // Jump -> W-Tap
            config.jumpResetUseWTap = true;
        } else if (config.jumpResetUseWTap) {
            // W-Tap -> S-Tap
            config.jumpResetUseWTap = false;
            config.jumpResetUseSTap = true;
        } else {
            // S-Tap -> Jump
            config.jumpResetUseSTap = false;
        }
        config.save();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
