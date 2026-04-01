package com.pvpclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PvpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("pvp-client.json");

    // === TriggerBot Settings ===
    public boolean triggerBotEnabled = false;
    /** Minimum attack cooldown % before triggering (0.0 - 1.0). 1.0 = full charge */
    public float triggerMinCooldown = 0.9f;
    /** Extra delay (ms) after cooldown is ready before attacking. Adds humanization */
    public int triggerDelayMs = 0;
    /** Randomize delay by +/- this many ms for anti-detection */
    public int triggerRandomMs = 20;
    /** Only trigger when crosshair is on entity */
    public boolean triggerRequireCrosshair = true;
    /** Max reach distance to trigger attack */
    public float triggerReach = 3.0f;
    /** Prioritize crits (only attack while falling) */
    public boolean triggerCritsOnly = false;
    /** Target players only (ignore mobs) */
    public boolean triggerPlayersOnly = false;

    // === Jump Reset Settings ===
    public boolean jumpResetEnabled = false;
    /** Auto-sprint before hitting */
    public boolean jumpResetAutoSprint = true;
    /** Jump after landing a hit to reset sprint */
    public boolean jumpResetAfterHit = true;
    /** Delay (ms) after hit before jumping */
    public int jumpResetDelayMs = 50;
    /** W-tap instead of jump (release W briefly to reset sprint) */
    public boolean jumpResetUseWTap = false;
    /** W-tap release duration in ms */
    public int wTapReleaseDurationMs = 50;
    /** S-tap instead (tap S briefly) */
    public boolean jumpResetUseSTap = false;
    /** S-tap duration in ms */
    public int sTapDurationMs = 30;
    /** Only jump reset when target is within combo range */
    public float jumpResetMaxRange = 4.0f;

    // === Shield Disable Settings ===
    public boolean shieldDisableEnabled = false;
    /** Auto-detect when target is blocking with shield */
    public boolean shieldAutoDetect = true;
    /** Hotbar slot index (0-8) where axe is kept */
    public int shieldAxeSlot = 1;
    /** Auto switch to axe when shield detected, hit, switch back */
    public boolean shieldAutoSwitch = true;
    /** Delay (ms) between switching to axe and attacking */
    public int shieldSwitchDelayMs = 50;
    /** Delay (ms) after axe hit before switching back to sword */
    public int shieldSwitchBackDelayMs = 50;

    // === HUD Settings ===
    public boolean hudEnabled = true;
    public int hudX = 5;
    public int hudY = 5;

    public void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static PvpConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                PvpConfig config = GSON.fromJson(json, PvpConfig.class);
                if (config != null) return config;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        PvpConfig config = new PvpConfig();
        config.save();
        return config;
    }
}
