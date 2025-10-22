package com.quake.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.quake.QuakeEntity;

@Mixin(value = MinecraftClient.class, priority = Integer.MAX_VALUE)
public abstract class QuakePlayerTicker {
    // This logic updates our player at a higher tickrate. Kind of a hack,
    // but this is the most reliable way of handling this.
    @Shadow public ClientPlayerEntity player;
    @Inject(at = @At("HEAD"), method = "render")
    private void render(boolean tick, CallbackInfo ci) {
        if(player == null
            || player.input == null
			|| !QuakeEntity.quakeEnabled(player)){
			// Cancel, as quake movement does not apply.
            return;
        }

        final Vec3d input = new Vec3d(
            player.input.movementSideways,
            0.0,
            player.input.movementForward
        );

		// Update our quake movement :-)
		player.input.tick(false, 0.0f);
		player.travel(input);
    }
}
