package com.quake.mixin;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.spongepowered.asm.mixin.Mixin;
import com.mojang.authlib.GameProfile;
import com.quake.QuakeCollider;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

@Mixin(value = ClientPlayerEntity.class, priority = 1)
public abstract class QuakeClientEntity extends PlayerEntity {
	private static final float INV_NANOSECOND = 1.0f / 1000000000.0f;
	private static final float RAD = 0.01745f;

	private static final Vec3d GRAVITY = new Vec3d(0.0, -0.05, 0.0);
	private static final Vec3d XZ = new Vec3d(1.0, 0.0, 1.0);

	private long previousLadderUpdate = System.nanoTime();
	private long previousQuakeUpdate = System.nanoTime();
	private long previousJumpTick = System.currentTimeMillis();

	private AtomicReference<Vec3d> cameraOffset = new AtomicReference<Vec3d>(Vec3d.ZERO);
	private double dt = 0.0;

	private boolean isGrounded;
	private boolean wasJumping;
	private Vec3d inertia = Vec3d.ZERO;

	public QuakeClientEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
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
		final float accelerate,
		final float maxVelocity
	) {
		// Constrain min(v.(i + i*dt), maxVel)
		final double projection = velocity.dotProduct(input);
		final double accel = Math.max(Math.min(accelerate * dt, maxVelocity - projection), 0.0);
		return velocity.add(input.multiply(accel));
	}

	private final float quakeGetMovementSpeed() {
		// Gets the player's movement speed
		return getMovementSpeed() * (this.isSneaking() ? 0.6f : this.isSprinting() ? 2.0f : 1.0f) * 2.0f;
	}

	private final Vec3d quakeMoveGround(
		final Vec3d input,
		Vec3d velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * 20.0 * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			4.5f,
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
			final double drop = speed * 5.0 * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		return quakeAccelerate(
			input,
			velocity,
			6.0f,
			quakeGetMovementSpeed() * 4.0f
		);
	}

	private final Vec3d quakeMoveLadder(
		Vec3d input,
		Vec3d velocity
	) {
		// Apply friction
		final double speed = velocity.length();
		if(speed > 0.0f) {
			final double drop = speed * 20.0 * dt;
			velocity = velocity.multiply(Math.max(speed - drop, 0.0) / speed);
		}

		Vec3d normal = QuakeCollider.quakeGetWall(this, input);
		input = input.subtract(normal.multiply(Math.min(input.dotProduct(normal), 0.0)));

		return quakeAccelerate(
			input,
			velocity,
			4.5f,
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
			2.0f,
			0.015f
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
			4.0f,
			0.03f
		);
	}

	private boolean quakeEnabled() {
		return this.getVehicle() == null;
	}

	private void quakeJump() {
		// Set player state airborne.
		final Vec3d velocity = this.getVelocity()
			.multiply(XZ)
			.add(new Vec3d(0.0, (this.getJumpVelocity() - this.getJumpBoostVelocityModifier()) * 0.8, 0.0))
			.add(inertia.multiply(1.5));
		this.setVelocity(velocity);
	}

	private void quakeTickBoost() {
		// Allows entity boosting to occur
		final Box playerBox = this.getBoundingBox();
		final double minY = playerBox.minY + 0.2;
		final Vec3d center = playerBox.getCenter();
		final List<Entity> list = this
			.getWorld()
			.getOtherEntities(this, playerBox.expand(0.01), ((hit) -> {
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
			this.setPos(center.x, currentBox.maxY + 0.001, center.z);
			this.setOnGround(true);

			inertia = new Vec3d(
				(ent.getX() - ent.prevX),
				(ent.getY() - ent.prevY),
				(ent.getZ() - ent.prevZ)
			);
		}
	}

	private void quakeTickMovement() {
		if(this.getAbilities().allowFlying
			&& !this.isGrounded
			&& wasJumping != this.jumping
			&& this.jumping)
		{
			// Check if we're trying to fly in creative mode.
			final long jumpTick = System.currentTimeMillis();
			if(jumpTick - previousJumpTick < 500) {
				this.getAbilities().flying = !this.getAbilities().flying;
			}

			previousJumpTick = jumpTick;
		}

		wasJumping = this.jumping;
		quakeTickBoost();
	}

	@Override
	public void setOnGround(boolean x) {
		isGrounded = x;
	}

	@Override
	public void travel(final Vec3d input) {
		// Update timer
		final long tick = System.nanoTime();
		dt = Math.min((tick - previousQuakeUpdate) * INV_NANOSECOND, 1.0/30.0);
		quakeTickMovement();

		// State variables
		final float pitch = this.getPitch() % 360.0f;
		final float yaw = this.getYaw() % 360.0f;

		final Vec3d wishDirection = quakeGetWishDirection(input, yaw * RAD);
		if(!this.quakeEnabled()) {
			// Ensure our entity interpolation
			// is still smooth. :-)
			this.limbAnimator.updateLimbs(0.0F, 0.4F);
			previousQuakeUpdate = tick;

			return;
		} else if(this.getAbilities().flying || this.noClip) {
			// Player is flying
			Vec3d flyDirection = quakeGetWishDirection(input, yaw * RAD, pitch * RAD);

			final double invSqrt = 1.0 / Math.sqrt(flyDirection.x*flyDirection.x + flyDirection.z*flyDirection.z);
			final Vec3d right = new Vec3d(flyDirection.z * invSqrt, 0.0, -flyDirection.x * invSqrt);
			final Vec3d up = right.crossProduct(flyDirection);

			// Set velocity
			flyDirection.add(up.multiply(this.isSneaking() ? -1.0 : this.jumping ? 1.0 : 0.0));
			this.setVelocity(quakeMoveFly(flyDirection, this.getVelocity()));
		} else if(this.isClimbing() && previousLadderUpdate < tick) {
			// Player is climbing
			final double y = -Math.sin(pitch * RAD) * input.z;
			if(this.jumping) {
				// Jumping off a ladder
				Vec3d normal = QuakeCollider.quakeGetWall(this, input);

				this.setVelocity(normal.multiply(0.3).add(0.0, 0.2, 0.0));
				this.setJumping(false);
				previousLadderUpdate = tick + 300000L;
			} else {
				this.setVelocity(quakeMoveLadder(wishDirection.add(0, y, 0), this.getVelocity()));
			}
		} else if(this.isSwimming()) {
			// Player is swimming
			this.setVelocity(quakeMoveSwim(wishDirection, this.getVelocity()));
		} else if(!this.isGrounded || this.getVelocity().y > 0.0) {
			// Player is airborne
			if(inertia != Vec3d.ZERO) {
				// Apply inertia
				this.addVelocity(inertia);
				inertia = Vec3d.ZERO;
			}

			this.setVelocity(quakeMoveAir(wishDirection, this.getVelocity()));
		} else if(this.jumping){
			// Player is jumping
			quakeJump();
		} else {
			// Player is on the ground
			this.setVelocity(quakeMoveGround(wishDirection, this.getVelocity()));
		}

		// Set position, then clip
		Vec3d delta = this.getVelocity().add(inertia).multiply(dt * 20.0);
		this.limbAnimator.updateLimbs(0.0F, 0.4F);
		this.bodyYaw = this.headYaw;

		// Set camera offset
		final double getupSpeed = Math.max(delta.length(), 6.0 * dt);
		final double y = this.cameraOffset.get().y + Math.max(Math.min(-this.cameraOffset.get().y, getupSpeed), -getupSpeed);
		this.cameraOffset.set(new Vec3d(0.0, y, 0.0));

		previousQuakeUpdate = tick;
		this.move(MovementType.SELF, delta);
	}

	@Override
	public void takeKnockback(double strength, double x, double z) {
		strength *= 1.0 - this.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		this.setOnGround(false);

		// Apply knockback
		final Vec3d velocity = this.getVelocity();
		final double invMagnitude = strength / Math.sqrt(x*x + z*z);
		final Vec3d newVelocity = velocity
			.multiply(0.5)
			.subtract(x*invMagnitude, -strength, z*invMagnitude);

		this.setVelocity(
			newVelocity.x,
			Math.min(0.4, newVelocity.y),
			newVelocity.z
		);
	}

	@Override
	public void move(MovementType type, Vec3d delta) {
		super.setOnGround(true);
		if(this.isGrounded && this.isSneaking() && !this.jumping) {
			// Handle special case sneaking collision :c
			QuakeCollider.quakeCollide(
				this,
				isGrounded,
				delta,
				cameraOffset
			);

			QuakeCollider.quakeSneak(this);
		} else if(this.noClip) {
			// Noclip case
			this.setPosition(this.getPos().add(delta));
		} else {
			// General case
			QuakeCollider.quakeCollide(
				this,
				isGrounded,
				delta,
				cameraOffset
			);
		}

		if(inertia != Vec3d.ZERO) {
			// Player boost
			this.setOnGround(true);
		}
	}

	@Override
	public Vec3d getLerpedPos(final float tickDelta) {
		if(this.quakeEnabled()) {
			// Provides the render offset
			return this.getPos();
		} else {
			// Use Minecraft's default interpolation.
			double d = MathHelper.lerp((double)tickDelta, this.prevX, this.getX());
			double e = MathHelper.lerp((double)tickDelta, this.prevY, this.getY());
			double f = MathHelper.lerp((double)tickDelta, this.prevZ, this.getZ());
			return new Vec3d(d, e, f);
		}
	}

	@Override
	public Vec3d getCameraPosVec(final float tickDelta) {
		// Get interpolated offset
		return getLerpedPos(tickDelta)
			.add(0.0, (double)this.getStandingEyeHeight(), 0.0)
			.add(cameraOffset.get());
	}

	@Override
	public void jump() { }
}
