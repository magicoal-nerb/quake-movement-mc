package com.quake;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class QuakeEntity {
	private static final float RAD = 0.01745f;
	private static final Vec3d GRAVITY = new Vec3d(0.0, -0.05, 0.0);
	private static final Vec3d XZ = new Vec3d(1.0, 0.0, 1.0);

	private long previousLadderUpdate = System.nanoTime();
	private AtomicReference<Vec3d> cameraOffset = new AtomicReference<Vec3d>(Vec3d.ZERO);
	private double dt = 0.0;

	private Vec3d inertia = Vec3d.ZERO;
	private boolean isGrounded;
	private PlayerEntity entity;

	public QuakeEntity(PlayerEntity ent) {
		// We just need to wrap a player entity
		// with our quake entity and thats it lol
		entity = ent;
	}

	// Checks if quake movement should be enabled.
	public boolean quakeEnabled() {
		return quakeEnabled(entity);
	}

	public static boolean quakeEnabled(Entity entity) {
		return QuakeConvars.pl_enabled == 1.0 && entity.getVehicle() == null;
	}

	private static final Vec3d quakeGetWishDirection(
		final Vec3d input,
		final float yaw
	) {
		/**
			[ cos(theta) 0 -sin(theta) ] [ x ]
			[     0      0       0     ] [ 0 ]
			[ sin(theta) 0  cos(theta) ] [ z ]
		**/

		if(input.lengthSquared() > 0.1){
			final float cos = MathHelper.cos(yaw);
			final float sin = MathHelper.sin(yaw);
			return new Vec3d(
				input.x * cos - input.z * sin,
				0,
				sin * input.x + cos * input.z
			).normalize();
		} else {
			return Vec3d.ZERO;
		}
	}

	private static final Vec3d quakeGetWishDirection(
		final Vec3d input,
		final float yaw,
		final float pitch
	) {
		/**
		 	Spherical coordinates:
			forward = [sin(yaw)cos(pitch), sin(pitch), sin(yaw)cos(pitch)]
			right = [sin(yaw+pi/2), 0, cos(yaw+pi/2)] =>
				sin(yaw)cos(pi/2) + sin(pi/2)cos(yaw) -> cos(yaw)
				cos(yaw)sin(pi/2) - cos(pi/2)sin(yaw) -> -sin(yaw)

			-> normalize(forward * input.z + right * input.x)
		**/

		if(input.lengthSquared() > 0.1){
			final double phi = Math.cos(pitch);
			final double sin = -Math.sin(yaw);
			final double cos = Math.cos(yaw);
			return new Vec3d(
				sin * phi * input.z + cos * input.x,
				-Math.sin(pitch) * input.z,
				cos * phi * input.z - sin * input.x
			).normalize();
		} else {
			return Vec3d.ZERO;
		}
	}

	private final Vec3d quakeAccelerate(
		final Vec3d input,
		final Vec3d velocity,
		final double accelerate,
		final double maxVelocity
	) {
		// Constrain min(v.(i + i*dt), maxVel)
		final double projection = velocity.dotProduct(input);
		final double accel = Math.max(Math.min(accelerate * dt, maxVelocity - projection), 0.0);
		return velocity.add(input.multiply(accel));
	}

	private final double quakeGetMovementSpeed() {
		// Gets the player's movement speed
		return entity.getMovementSpeed() * (entity.isSneaking() ? 0.6f : entity.isSprinting() ? 2.0f : 1.0f) * 2.0f;
	}

	private final Vec3d quakeMoveGround(
		final Vec3d input,
		Vec3d velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_ground_friction * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_ground_acceleration,
			quakeGetMovementSpeed()
		).multiply(XZ);
	}

	private final Vec3d quakeMoveFly(
		Vec3d input,
		Vec3d velocity
	){
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_fly_friction * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_fly_accelerate,
			quakeGetMovementSpeed() * 4.0
		);
	}

	private final Vec3d quakeMoveLadder(
		Vec3d input,
		Vec3d velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_ground_friction * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		Vec3d normal = QuakeCollider.quakeGetWall(entity, input);
		input = input.subtract(normal.multiply(Math.min(input.dotProduct(normal), 0.0)));

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_ladder_accelerate,
			quakeGetMovementSpeed()
		);
	}

	private final Vec3d quakeMoveSwim(
		final Vec3d input,
		final Vec3d velocity
	) {
		// Apply gravity
		return quakeAccelerate(
			input,
			velocity.add(GRAVITY.multiply(dt * 5.0)),
			QuakeConvars.pl_swim_accelerate,
			QuakeConvars.pl_swim_speed
		);
	}

	private final Vec3d quakeMoveAir(
		final Vec3d input,
		final Vec3d velocity
	) {
		// Apply gravity
		return quakeAccelerate(
			input,
			velocity.add(GRAVITY.multiply(dt * 20.0)),
			QuakeConvars.pl_air_accelerate,
			QuakeConvars.pl_air_speed
		);
	}

	private void quakeJump() {
		// Set player state airborne.
		final Vec3d velocity = entity.getVelocity()
			.multiply(XZ)
			.add(new Vec3d(0.0, (entity.getJumpVelocity() - entity.getJumpBoostVelocityModifier()) * 0.8, 0.0))
			.add(inertia);
		entity.setVelocity(velocity);
	}

	private void quakeTickBoost() {
		// Allows entity boosting to occur
		final Box playerBox = entity.getBoundingBox();
		final double minY = playerBox.minY + 0.2;
		final Vec3d center = playerBox.getCenter();
		final List<Entity> list = entity
			.getWorld()
			.getOtherEntities(entity, playerBox.expand(0.15), ((hit) -> {
				return true;
			}));

		// Apply player inertia if possible lol
		double bestDifference = 0.333;
		int bestIndex = -1;

		inertia = Vec3d.ZERO;
		for(int i = 0; i < list.size(); i++) {
			final Box currentBox = list.get(i).getBoundingBox();
			final double difference = minY - currentBox.maxY;
			if(difference >= 0.0 && difference < bestDifference) {
				// We can boost from the entity.
				bestDifference = difference;
				bestIndex = i;
			}
		}

		if(bestIndex != -1) {
			// Set inertia
			final Entity ent = list.get(bestIndex);
			final Box currentBox = ent.getBoundingBox();
			entity.setPos(center.x, currentBox.maxY + 0.15, center.z);
			entity.setOnGround(true);

			inertia = new Vec3d(
				(ent.getX() - ent.prevX),
				(ent.getY() - ent.prevY),
				(ent.getZ() - ent.prevZ)
			);
		}
	}

	// Minecraft overrides
	public void minecraftSetOnGround(boolean x) {
		isGrounded = x;
	}

	public void minecraftTravel(final Vec3d input, final double dt) {
		// Update timer
		final long tick = System.nanoTime();
		this.dt = dt;
		quakeTickBoost();

		// State variables
		final float pitch = entity.getPitch() % 360.0f;
		final float yaw = entity.getYaw() % 360.0f;

		final Vec3d wishDirection = quakeGetWishDirection(input, yaw * RAD);
		if(entity.getAbilities().flying || entity.noClip) {
			// Player is flying
			Vec3d flyDirection = quakeGetWishDirection(input, yaw * RAD, pitch * RAD);

			final double invSqrt = 1.0 / Math.sqrt(flyDirection.x*flyDirection.x + flyDirection.z*flyDirection.z);
			final Vec3d right = new Vec3d(flyDirection.z * invSqrt, 0.0, -flyDirection.x * invSqrt);
			final Vec3d up = right.crossProduct(flyDirection);

			// Set velocity
			flyDirection.add(up.multiply(entity.isSneaking() ? -1.0 : entity.jumping ? 1.0 : 0.0));
			entity.setVelocity(quakeMoveFly(flyDirection, entity.getVelocity()));
		} else if(entity.isClimbing() && previousLadderUpdate < tick) {
			// Player is climbing
			final double y = -Math.sin(pitch * RAD) * input.z;
			if(entity.jumping) {
				// Jumping off a ladder, also make sure there's
				// some delay here haha
				Vec3d normal = QuakeCollider.quakeGetWall(entity, input);
				entity.setVelocity(normal.multiply(0.3).add(0.0, 0.2, 0.0));
				entity.setJumping(false);

				previousLadderUpdate = tick + 300000L;
			} else {
				entity.setVelocity(quakeMoveLadder(wishDirection.add(0, y, 0), entity.getVelocity()));
			}
		} else if(entity.isSwimming()) {
			// Player is swimming
			entity.setVelocity(quakeMoveSwim(wishDirection, entity.getVelocity()));
		} else if(!isGrounded || entity.getVelocity().y > 0.0) {
			// Player is airborne
			if(inertia != Vec3d.ZERO) {
				// Apply inertia
				entity.addVelocity(inertia);
				inertia = Vec3d.ZERO;
			}

			entity.setVelocity(quakeMoveAir(wishDirection, entity.getVelocity()));
		} else if(entity.jumping){
			// Player is jumping
			quakeJump();
		} else {
			// Player is on the ground
			entity.setVelocity(quakeMoveGround(wishDirection, entity.getVelocity()));
		}

		// Set position, then clip
		Vec3d delta = entity.getVelocity().add(inertia).multiply(dt * 20.0);
		entity.limbAnimator.updateLimbs(0.0F, 0.4F);
		entity.bodyYaw = entity.headYaw;

		// Set camera offset
		final double getupSpeed = Math.max(delta.length(), 6.0 * dt);
		final double y = this.cameraOffset.get().y + Math.max(Math.min(-this.cameraOffset.get().y, getupSpeed), -getupSpeed);
		this.cameraOffset.set(new Vec3d(0.0, y, 0.0));

		minecraftMove(MovementType.SELF, delta);
	}

	public void minecraftTakeKnockback(double strength, double x, double z) {
		strength *= 1.0 - entity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		entity.setOnGround(false);

		// Apply knockback
		final Vec3d velocity = entity.getVelocity();
		final double invMagnitude = strength / Math.sqrt(x*x + z*z);
		final Vec3d newVelocity = velocity
			.multiply(0.5)
			.subtract(x*invMagnitude, -strength, z*invMagnitude);

			entity.setVelocity(
			newVelocity.x,
			Math.min(0.4, newVelocity.y),
			newVelocity.z
		);
	}

	public void minecraftMove(MovementType type, Vec3d delta) {
		entity.setOnGround(true);
		if(this.isGrounded && entity.isSneaking() && !entity.jumping) {
			// Handle special case sneaking collision :c
			QuakeCollider.quakeCollide(
				entity,
				isGrounded,
				delta,
				cameraOffset
			);

			QuakeCollider.quakeSneak(entity);
		} else if(entity.noClip) {
			// Noclip case
			entity.setPosition(entity.getPos().add(delta));
		} else {
			// General case
			QuakeCollider.quakeCollide(
				entity,
				isGrounded,
				delta,
				cameraOffset
			);
		}

		if(inertia != Vec3d.ZERO) {
			// Player boost
			entity.setOnGround(true);
		}
	}

	public Vec3d minecraftGetLerpedPos(final float tickDelta) {
		if(quakeEnabled()) {
			// Provides the render offset
			return entity.getPos();
		} else {
			// Use Minecraft's default interpolation.
			double d = MathHelper.lerp((double)tickDelta, entity.prevX, entity.getX());
			double e = MathHelper.lerp((double)tickDelta, entity.prevY, entity.getY());
			double f = MathHelper.lerp((double)tickDelta, entity.prevZ, entity.getZ());
			return new Vec3d(d, e, f);
		}
	}

	public Vec3d minecraftGetCameraPosVec(final float tickDelta) {
		// Get interpolated offset
		return minecraftGetLerpedPos(tickDelta)
			.add(0.0, (double)entity.getStandingEyeHeight(), 0.0)
			.add(cameraOffset.get());
	}
}
