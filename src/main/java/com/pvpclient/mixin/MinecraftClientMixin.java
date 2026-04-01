package com.pvpclient.mixin;

import com.pvpclient.PvpClientMod;
import com.pvpclient.jumpreset.JumpResetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /**
     * Hook into the attack method to notify JumpResetManager when we land a hit.
     * This is the key integration point - after any attack (manual or triggerbot),
     * the jump reset system can trigger a sprint reset.
     */
    @Inject(method = "doAttack", at = @At("RETURN"))
    private void onDoAttack(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null) return;

        // Notify jump reset manager about the attack
        JumpResetManager jrm = PvpClientMod.getJumpResetManager();
        if (jrm != null && jrm.isEnabled()) {
            // The target is whoever we just hit via crosshairTarget
            if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entityHit) {
                Entity entity = entityHit.getEntity();
                if (entity instanceof LivingEntity living) {
                    jrm.onAttackEntity(living);
                }
            }
        }
    }
}
