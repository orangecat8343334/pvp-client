package com.pvpclient.mixin;

import com.pvpclient.PvpClientMod;
import com.pvpclient.jumpreset.JumpResetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void pvpClient$beforeTickMovement(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        JumpResetManager jumpResetManager = PvpClientMod.getJumpResetManager();
        if (jumpResetManager == null || !jumpResetManager.isManipulatingMovement()) return;

        jumpResetManager.applyMovementOverride(mc, (ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void pvpClient$afterTickMovement(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        JumpResetManager jumpResetManager = PvpClientMod.getJumpResetManager();
        if (jumpResetManager == null || !jumpResetManager.isManipulatingMovement()) return;

        jumpResetManager.restoreMovementOverride(mc, (ClientPlayerEntity) (Object) this);
    }
}
