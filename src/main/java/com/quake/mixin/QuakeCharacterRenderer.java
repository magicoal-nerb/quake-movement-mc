package com.quake.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Mixin(value = LevelRenderer.class, priority = Integer.MAX_VALUE)
public abstract class QuakeCharacterRenderer {
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    /**
     * @author magical
     * @reason adds interpolation
     */
    @Overwrite
    private void renderEntity(
        Entity entity,
        double camX,
        double camY,
        double camZ,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource
    ) {
        // getPosition(partialTick) is redirected for the quake player
        // by QuakeEntityPosition.
        Vec3 lerped = entity.getPosition(partialTick);
        float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        this.entityRenderDispatcher.render(
            entity,
            lerped.x - camX,
            lerped.y - camY,
            lerped.z - camZ,
            yRot,
            partialTick,
            poseStack,
            bufferSource,
            this.entityRenderDispatcher.getPackedLightCoords(entity, partialTick)
        );
    }
}