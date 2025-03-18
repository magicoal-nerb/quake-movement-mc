package com.quake.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class, priority = Integer.MAX_VALUE)
public abstract class QuakePlayerTicker {
    // Allows the client to update our player movement at 128 tick
    @Shadow public ClientPlayerEntity player;
    @Inject(at = @At("HEAD"), method = "render")
    private void render(boolean tick, CallbackInfo ci) {
        if(player == null 
            || player.input == null){
            return;
        }

        final ClientPlayerEntity entity = (ClientPlayerEntity)((Object)player);
        final Vec3d input = new Vec3d(
            player.input.movementSideways,
            0.0,
            player.input.movementForward
        );
        
        // Update our quake movement :-)
        entity.input.tick(false, 0.0f);
        entity.travel(input);
    }
}