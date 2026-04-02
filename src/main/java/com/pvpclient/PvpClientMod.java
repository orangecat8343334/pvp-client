package com.pvpclient;

import com.pvpclient.config.PvpConfig;
import com.pvpclient.gui.PvpSettingsScreen;
import com.pvpclient.triggerbot.TriggerBot;
import com.pvpclient.jumpreset.DefensiveJumpReset;
import com.pvpclient.jumpreset.JumpResetManager;
import com.pvpclient.shielddisable.ShieldDisableManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PvpClientMod implements ClientModInitializer {
    public static final String MOD_ID = "pvp-client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Keybinds
    private static KeyBinding toggleTriggerBot;
    private static KeyBinding toggleJumpReset;
    private static KeyBinding toggleShieldDisable;
    private static KeyBinding toggleKBReduce;
    private static KeyBinding openSettings;

    // Modules
    private static TriggerBot triggerBot;
    private static JumpResetManager jumpResetManager;
    private static ShieldDisableManager shieldDisableManager;
    private static DefensiveJumpReset defensiveJumpReset;
    private static PvpConfig config;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[PVP Client] Initializing...");

        config = PvpConfig.load();

        // Initialize modules
        triggerBot = new TriggerBot(config);
        jumpResetManager = new JumpResetManager(config);
        shieldDisableManager = new ShieldDisableManager(config);
        defensiveJumpReset = new DefensiveJumpReset(config);

        // Register keybinds
        toggleTriggerBot = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvp-client.toggle_triggerbot",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
                "category.pvp-client"
        ));
        toggleJumpReset = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvp-client.toggle_jumpreset",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
                "category.pvp-client"
        ));
        toggleShieldDisable = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvp-client.toggle_shielddisable",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
                "category.pvp-client"
        ));
        toggleKBReduce = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvp-client.toggle_kbreduce",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "category.pvp-client"
        ));
        openSettings = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvp-client.open_settings",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON,
                "category.pvp-client"
        ));

        // Tick handler
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        LOGGER.info("[PVP Client] Loaded - TB [R] | JR [J] | SD [G] | KB [K] | Settings [;]");
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Handle keybind presses
        while (toggleTriggerBot.wasPressed()) triggerBot.toggle();
        while (toggleJumpReset.wasPressed()) jumpResetManager.toggle();
        while (toggleShieldDisable.wasPressed()) shieldDisableManager.toggle();
        while (toggleKBReduce.wasPressed()) defensiveJumpReset.toggle();
        while (openSettings.wasPressed()) {
            client.setScreen(new PvpSettingsScreen(null, config, triggerBot, jumpResetManager, shieldDisableManager));
        }

        // Tick each module
        if (shieldDisableManager.isEnabled()) shieldDisableManager.tick(client);
        if (triggerBot.isEnabled()) triggerBot.tick(client);
        if (jumpResetManager.isEnabled()) jumpResetManager.tick(client);
        if (defensiveJumpReset.isEnabled()) defensiveJumpReset.tick(client);
    }

    public static void notifyAttackLanded(LivingEntity target) {
        if (target == null || jumpResetManager == null || !jumpResetManager.isEnabled()) {
            return;
        }

        jumpResetManager.onAttackEntity(target);
    }

    public static TriggerBot getTriggerBot() { return triggerBot; }
    public static JumpResetManager getJumpResetManager() { return jumpResetManager; }
    public static ShieldDisableManager getShieldDisableManager() { return shieldDisableManager; }
    public static DefensiveJumpReset getDefensiveJumpReset() { return defensiveJumpReset; }
    public static PvpConfig getConfig() { return config; }
}
