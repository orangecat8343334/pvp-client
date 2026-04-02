package com.pvpclient.mixin;

import com.pvpclient.PvpClientMod;
import com.pvpclient.config.PvpConfig;
import com.pvpclient.triggerbot.NoMissDelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    /**
     * After an attack lands, notify JumpResetManager to start sprint reset.
     */
    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void pvpClient$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof LivingEntity livingTarget) {
            PvpClientMod.notifyAttackLanded(livingTarget);
        }
    }

    /**
     * NoMissDelay: cancel the attack action if crosshair is on air/blocks.
     * Prevents wasting the attack cooldown on a miss.
     * Hooks doAttack (returns boolean) on MinecraftClient — but since that's
     * harder to cancel, we hook hasLimitedAttackSpeed or handle via the
     * attack method. For simplicity, this uses a flag approach.
     */
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void pvpClient$noMissCheck(PlayerEntity player, Entity target, CallbackInfo ci) {
        // NoMissDelay only cancels air/block attacks, not entity attacks.
        // Entity attacks always pass through — this hook is for the entity path.
        // The real miss-prevention happens in the doAttack mixin.
    }
}
