package com.quake.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.quake.QuakeEntity;
import com.quake.QuakePlayer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class QuakeEntityPosition {
    @Inject(
        method = "getPosition(F)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void quake$getPosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if((Object)this instanceof QuakePlayer player) {
            final QuakeEntity quake = player.quake$get();
            if(quake != null) {
                cir.setReturnValue(quake.minecraftGetLerpedPos(partialTick));
            }
        }
    }

    @Inject(
        method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void quake$getEyePosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if((Object)this instanceof QuakePlayer player) {
            final QuakeEntity quake = player.quake$get();
            if(quake != null) {
                cir.setReturnValue(quake.minecraftGetCameraPosVec(partialTick));
            }
        }
    }
}