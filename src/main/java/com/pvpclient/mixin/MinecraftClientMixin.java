package com.pvpclient.mixin;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.triggerbot.NoMissDelay;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /**
     * NoMissDelay: Cancel doAttack() if crosshair is on air or blocks.
     * This prevents the attack cooldown from being wasted on misses.
     * doAttack returns boolean — cancel with ci.setReturnValue(false).
     */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void pvpClient$noMissDelay(CallbackInfoReturnable<Boolean> cir) {
        PvpConfig config = PvpClientMod.getConfig();
        if (config == null || !config.noMissDelayEnabled) return;

        MinecraftClient client = (MinecraftClient) (Object) this;
        if (NoMissDelay.shouldCancelAttack(client, config.noMissOnlyWeapons, config.noMissCancelAir, config.noMissCancelBlocks)) {
            cir.setReturnValue(false);
        }
    }
}
