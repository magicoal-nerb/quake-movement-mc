package com.quake.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

@Mixin(value = Camera.class, priority = Integer.MAX_VALUE)
public abstract class QuakeClientCamera {
	@Shadow private Entity focusedEntity;
    @Shadow protected void setRotation(float x, float y) { }
    @Shadow protected void setPos(Vec3d pos) { }
    @Shadow protected void moveBy(double x, double y, double z) { };
    @Shadow private double clipToSpace(double x) { return 0.0; };
    @Shadow private boolean ready;
    @Shadow private BlockView area;
    @Shadow private boolean thirdPerson;
    @Shadow private float yaw;
    @Shadow private float pitch;

    @Inject(at = @At("TAIL"), method = "update")
    public void update(
        BlockView area,
        Entity focusedEntity,
        boolean thirdPerson,
        boolean inverseView,
        float tickDelta,
        CallbackInfo inf
    ) {
		// Apply the newly interpolated position of our entity
    	// instead.
        this.setRotation(focusedEntity.getYaw(tickDelta), focusedEntity.getPitch(tickDelta));
        this.setPos(focusedEntity.getCameraPosVec(tickDelta));

		if (thirdPerson) {
			if (inverseView) {
				this.setRotation(this.yaw + 180.0F, -this.pitch);
			}

			this.moveBy(-this.clipToSpace(4.0), 0.0, 0.0);
		} else if (focusedEntity instanceof LivingEntity && ((LivingEntity)focusedEntity).isSleeping()) {
			Direction direction = ((LivingEntity)focusedEntity).getSleepingDirection();
			this.setRotation(direction != null ? direction.asRotation() - 180.0F : 0.0F, 0.0F);
			this.moveBy(0.0, 0.3, 0.0);
		}
	}
}
