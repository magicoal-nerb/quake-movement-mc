package com.quake;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class QuakeEntity {
	private static final float RAD = 0.01745f;
	private static final Vec3 GRAVITY = new Vec3(0.0, -0.05, 0.0);
	private static final Vec3 XZ = new Vec3(1.0, 0.0, 1.0);

	private long previousLadderUpdate = System.nanoTime();
	private AtomicReference<Vec3> cameraOffset = new AtomicReference<Vec3>(Vec3.ZERO);
	private double dt = 0.0;

	private Vec3 inertia = Vec3.ZERO;
	private boolean isGrounded;

	private boolean desiredJumping;
	private boolean isJumping;
	private Player entity;

	public QuakeEntity(Player ent) {
		// We just need to wrap a player entity
		// with our quake entity and thats it lol
		entity = ent;
	}

	// Checks if quake movement should be enabled.
	public boolean quakeEnabled() {
		return quakeEnabled(entity);
	}

	public static boolean quakeEnabled(Entity entity) {
		return QuakeConvars.pl_enabled && entity.getVehicle() == null;
	}

	private static final Vec3 quakeGetWishDirection(
		final Vec3 input,
		final float yaw
	) {
		/**
			[ cos(theta) 0 -sin(theta) ] [ x ]
			[     0      0       0     ] [ 0 ]
			[ sin(theta) 0  cos(theta) ] [ z ]
		**/

		if(input.lengthSqr() > 0.1){
			final float cos = Mth.cos(yaw);
			final float sin = Mth.sin(yaw);
			return new Vec3(
				input.x * cos - input.z * sin,
				0,
				sin * input.x + cos * input.z
			).normalize();
		} else {
			return Vec3.ZERO;
		}
	}

	private static final Vec3 quakeGetWishDirection(
		final Vec3 input,
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

		if(input.lengthSqr() > 0.1){
			final double phi = Math.cos(pitch);
			final double sin = -Math.sin(yaw);
			final double cos = Math.cos(yaw);
			return new Vec3(
				sin * phi * input.z + cos * input.x,
				-Math.sin(pitch) * input.z,
				cos * phi * input.z - sin * input.x
			).normalize();
		} else {
			return Vec3.ZERO;
		}
	}

	private final Vec3 quakeAccelerate(
		final Vec3 input,
		final Vec3 velocity,
		final double accelerate,
		final double maxVelocity
	) {
		// Constrain min(v.(i + i*dt), maxVel)
		final double projection = velocity.dot(input);
		final double accel = Math.max(Math.min(accelerate * dt, maxVelocity - projection), 0.0);
		return velocity.add(input.scale(accel));
	}

	private final double quakeGetMovementSpeed() {
		// Gets the player's movement speed
		return entity.getSpeed() * (entity.isShiftKeyDown() ? 0.6f : entity.isSprinting() ? 2.0f : 1.0f) * 2.0f;
	}

	private final Vec3 quakeMoveGround(
		final Vec3 input,
		Vec3 velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_ground_friction * dt;
			velocity = velocity.scale(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_ground_acceleration,
			quakeGetMovementSpeed()
		).multiply(XZ);
	}

	private final Vec3 quakeMoveFly(
		Vec3 input,
		Vec3 velocity
	){
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_fly_friction * dt;
			velocity = velocity.scale(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_fly_accelerate,
			quakeGetMovementSpeed() * 4.0
		);
	}

	private final Vec3 quakeMoveLadder(
		Vec3 input,
		Vec3 velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * QuakeConvars.pl_ground_friction * dt;
			velocity = velocity.scale(Math.max(speed - drop, 0.0) / speed);
		}

		Vec3 normal = QuakeCollider.quakeGetWall(entity, input);
		input = input.subtract(normal.scale(Math.min(input.dot(normal), 0.0)));

		return quakeAccelerate(
			input,
			velocity,
			QuakeConvars.pl_ladder_accelerate,
			quakeGetMovementSpeed()
		);
	}

	private final Vec3 quakeMoveSwim(
		final Vec3 input,
		final Vec3 velocity
	) {
		// Apply gravity
		return quakeAccelerate(
			input,
			velocity.add(GRAVITY.scale(dt * 5.0)),
			QuakeConvars.pl_swim_accelerate,
			QuakeConvars.pl_swim_speed
		);
	}

	private final Vec3 quakeMoveAir(
		final Vec3 input,
		final Vec3 velocity
	) {
		// Apply gravity
		return quakeAccelerate(
			input,
			velocity.add(GRAVITY.scale(dt * 20.0)),
			QuakeConvars.pl_air_accelerate,
			QuakeConvars.pl_air_speed
		);
	}

	private double quakeGetJumpPower() {
    	return (float)entity.getAttributeValue(Attributes.JUMP_STRENGTH) + entity.getJumpBoostPower();
	}

	private void quakeJump() {
		// Set player state airborne.
		final Vec3 velocity = entity.getDeltaMovement()
			.multiply(XZ)
			.add(new Vec3(0.0, (quakeGetJumpPower() - entity.getJumpBoostPower()) * 0.8, 0.0))
			.add(inertia);

		if(!QuakeConvars.pl_autohop) {
			isJumping = false;
		}

		entity.setDeltaMovement(velocity);
	}

	private void quakeTickBoost() {
		// Allows entity boosting to occur
		final AABB playerBox = entity.getBoundingBox();
		final double minY = playerBox.minY + 0.2;
		final Vec3 center = playerBox.getCenter();
		final List<Entity> list = entity
			.level()
			.getEntities(entity, playerBox.inflate(0.15), ((hit) -> {
				return true;
			}));

		// Apply player inertia if possible lol
		double bestDifference = 0.333;
		int bestIndex = -1;

		inertia = Vec3.ZERO;
		for(int i = 0; i < list.size(); i++) {
			final AABB currentBox = list.get(i).getBoundingBox();
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
			final AABB currentBox = ent.getBoundingBox();
			entity.setPos(center.x, currentBox.maxY + 0.15, center.z);
			entity.setOnGround(true);

			inertia = new Vec3(
				(ent.getX() - ent.xo),
				(ent.getY() - ent.yo),
				(ent.getZ() - ent.zo)
			);
		}
	}

	public void quakeTickMovement() {
		if(desiredJumping != entity.jumping) {
			desiredJumping = entity.jumping;
			isJumping = desiredJumping;
		}
	}

	// Minecraft overrides
	public void minecraftSetOnGround(boolean x) {
		isGrounded = x;
	}

	public void minecraftTravel(final Vec3 input, final double dt) {
		// Update timer
		final long tick = System.nanoTime();
		this.dt = dt;

		quakeTickMovement();
		quakeTickBoost();

		// State variables
		final float pitch = entity.getXRot() % 360.0f;
		final float yaw = entity.getYRot() % 360.0f;

		final Vec3 wishDirection = quakeGetWishDirection(input, yaw * RAD);
		if(entity.getAbilities().flying || entity.noPhysics) {
			// Player is flying
			Vec3 flyDirection = quakeGetWishDirection(input, yaw * RAD, pitch * RAD);

			final double invSqrt = 1.0 / Math.sqrt(flyDirection.x*flyDirection.x + flyDirection.z*flyDirection.z);
			final Vec3 right = new Vec3(flyDirection.z * invSqrt, 0.0, -flyDirection.x * invSqrt);
			final Vec3 up = right.cross(flyDirection);

			// Set velocity
			flyDirection = flyDirection.add(up.scale(entity.isShiftKeyDown() ? -1.0 : isJumping ? 1.0 : 0.0));
			entity.setDeltaMovement(quakeMoveFly(flyDirection, entity.getDeltaMovement()));
		} else if(entity.onClimbable() && previousLadderUpdate < tick) {
			// Player is climbing
			final double y = -Math.sin(pitch * RAD) * input.z;
			if(isJumping) {
				// Jumping off a ladder, also make sure there's
				// some delay here haha
				Vec3 normal = QuakeCollider.quakeGetWall(entity, input);
				entity.setDeltaMovement(normal.scale(0.3).add(0.0, 0.2, 0.0));
				previousLadderUpdate = tick + 300000L;
			} else {
				entity.setDeltaMovement(quakeMoveLadder(wishDirection.add(0, y, 0), entity.getDeltaMovement()));
			}
		} else if(entity.isSwimming()) {
			// Player is swimming
			entity.setDeltaMovement(quakeMoveSwim(wishDirection, entity.getDeltaMovement()));
		} else if(!isGrounded || entity.getDeltaMovement().y > 0.0) {
			// Player is airborne
			if(inertia != Vec3.ZERO) {
				// Apply inertia
				entity.addDeltaMovement(inertia);
				inertia = Vec3.ZERO;
			}

			entity.setDeltaMovement(quakeMoveAir(wishDirection, entity.getDeltaMovement()));
		} else if(isJumping){
			// Player is jumping
			quakeJump();
		} else {
			// Player is on the ground
			entity.setDeltaMovement(quakeMoveGround(wishDirection, entity.getDeltaMovement()));
		}

		// Set position, then clip
		Vec3 velocity = entity.getDeltaMovement();

		final double speed = Math.sqrt(velocity.x*velocity.x + velocity.z*velocity.z);
		if(QuakeConvars.pl_speed_cap != 0.0 && speed > QuakeConvars.pl_speed_cap) {
			// Apply a hard cap if you're lame LOL!
			final double scale = Math.min(QuakeConvars.pl_speed_cap / speed, 1.0);
			velocity = velocity.multiply(scale, 1.0, scale);
			entity.setDeltaMovement(velocity);
		}

		Vec3 delta = velocity.add(inertia).scale(dt * 20.0);
		entity.walkAnimation.update(0.0F, 0.4F);
		entity.yBodyRot = entity.yHeadRot;

		// Set camera offset
		final double getupSpeed = Math.max(delta.length(), 6.0 * dt);
		final double y = this.cameraOffset.get().y + Math.max(Math.min(-this.cameraOffset.get().y, getupSpeed), -getupSpeed);
		this.cameraOffset.set(new Vec3(0.0, y, 0.0));

		entity.move(MoverType.SELF, delta);
	}

	public void minecraftTakeKnockback(double strength, double x, double z) {
		strength *= 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		entity.setOnGround(false);

		// Apply knockback
		final Vec3 velocity = entity.getDeltaMovement();
		final double invMagnitude = strength / Math.sqrt(x*x + z*z);
		final Vec3 newVelocity = velocity
			.scale(0.5)
			.subtract(x*invMagnitude, -strength, z*invMagnitude);

		entity.setDeltaMovement(
			newVelocity.x,
			Math.min(0.4, newVelocity.y),
			newVelocity.z
		);
	}

	public void minecraftMove(MoverType type, Vec3 delta) {
		if(isGrounded && entity.isShiftKeyDown() && !isJumping) {
			// Handle special case sneaking collision :c
			QuakeCollider.quakeCollide(
				entity,
				isGrounded,
				delta,
				cameraOffset
			);

			QuakeCollider.quakeSneak(entity);
		} else if(entity.noPhysics) {
			// Noclip case
			entity.setPos(entity.position().add(delta));
		} else {
			// General case
			QuakeCollider.quakeCollide(
				entity,
				isGrounded,
				delta,
				cameraOffset
			);
		}

		if(inertia != Vec3.ZERO) {
			// Player boost
			entity.setOnGround(true);
		}
	}

	public Vec3 minecraftGetLerpedPos(final float tickDelta) {
		if(quakeEnabled()) {
			// Provides the render offset
			return entity.position().add(cameraOffset.get());
		} else {
			// Use Minecraft's default interpolation.
			double d = Mth.lerp((double)tickDelta, entity.xo, entity.getX());
			double e = Mth.lerp((double)tickDelta, entity.yo, entity.getY());
			double f = Mth.lerp((double)tickDelta, entity.zo, entity.getZ());
			return new Vec3(d, e, f);
		}
	}

	public Vec3 minecraftGetCameraPosVec(final float tickDelta) {
		// Get interpolated offset
		return minecraftGetLerpedPos(tickDelta)
			.add(0.0, (double)entity.getEyeHeight(), 0.0);
	}
}