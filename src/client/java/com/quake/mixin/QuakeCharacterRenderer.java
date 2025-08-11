package com.quake.mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Mixin(value = WorldRenderer.class, priority = Integer.MAX_VALUE)
public abstract class QuakeCharacterRenderer {
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    /**
     * renderEntity
     * @author magical
     * @reason adds interpolation
     * @param entity
     * @param cameraX
     * @param cameraY
     * @param cameraZ
     * @param tickDelta
     * @param matrices
     * @param vertexConsumers
     */
    @Overwrite
    private void renderEntity(
        Entity entity,
        double cameraX,
        double cameraY,
        double cameraZ,
        float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers
    ) {
        Vec3d lerped = entity.getLerpedPos(tickDelta);
        float g = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        this.entityRenderDispatcher.render(
            entity,
            lerped.x - cameraX,
            lerped.y - cameraY,
            lerped.z - cameraZ,
            g,
            tickDelta,
            matrices,
            vertexConsumers,
            this.entityRenderDispatcher.getLight(entity, tickDelta)
        );
    }
}
