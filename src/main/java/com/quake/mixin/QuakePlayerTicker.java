package com.quake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.quake.QuakeEntity;

@Mixin(value = Minecraft.class, priority = Integer.MAX_VALUE)
public abstract class QuakePlayerTicker {
    // This logic updates our player at a higher tickrate. Kind of a hack,
    // but this is the most reliable way of handling this.
    @Shadow public LocalPlayer player;

    @Inject(at = @At("HEAD"), method = "runTick")
    private void quake$runTick(boolean renderLevel, CallbackInfo ci) {
        if(player == null
            || player.input == null
            || !QuakeEntity.quakeEnabled(player)){
            // Cancel, as quake movement does not apply.
            return;
        }

        final Vec3 input = new Vec3(
            player.input.leftImpulse,
            0.0,
            player.input.forwardImpulse
        );

        // Update our quake movement :-)
        player.input.tick(false, 0.0f);
        player.travel(input);
    }
}