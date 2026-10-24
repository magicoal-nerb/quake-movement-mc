package com.quake.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(value = Camera.class, priority = Integer.MAX_VALUE)
public abstract class QuakeClientCamera {
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void move(float zoom, float dy, float dx);
    @Shadow protected abstract float getMaxZoom(float maxZoom);

    @Inject(at = @At("TAIL"), method = "setup")
    private void quake$setup(
        BlockGetter level,
        Entity entity,
        boolean detached,
        boolean thirdPersonReverse,
        float partialTick,
        CallbackInfo ci
    ) {
        // REEEE
        this.setRotation(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
        this.setPosition(entity.getEyePosition(partialTick));

        if (detached) {
            if (thirdPersonReverse) {
                this.setRotation(this.yRot + 180.0F, -this.xRot);
            }

            final float scale = entity instanceof LivingEntity living ? living.getScale() : 1.0F;
            this.move(-this.getMaxZoom(4.0F * scale), 0.0F, 0.0F);
        } else if (entity instanceof LivingEntity living && living.isSleeping()) {
            Direction direction = living.getBedOrientation();
            this.setRotation(direction != null ? direction.toYRot() - 180.0F : 0.0F, 0.0F);
            this.move(0.0F, 0.3F, 0.0F);
        }
    }
}