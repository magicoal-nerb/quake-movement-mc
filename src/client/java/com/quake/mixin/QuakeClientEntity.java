package com.quake.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import com.mojang.authlib.GameProfile;
import com.quake.QuakeEntity;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

@Mixin(value = ClientPlayerEntity.class, priority = 1)
public abstract class QuakeClientEntity extends PlayerEntity {
	private static final float INV_NANOSECOND = 1.0f / 1000000000.0f;
	@Unique private static QuakeEntity entity;
	@Unique private long previousTick;

	public QuakeClientEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Inject(at = @At("HEAD"), method = "init()V")
	private void init(CallbackInfo ci) {
		entity = new QuakeEntity(this);
	}

	@Override
	public void setOnGround(boolean x) {
		entity.minecraftSetOnGround(x);
	}

	@Override
	public void travel(final Vec3d input) {
		// Update timer
		final long tick = System.nanoTime();
		double dt = Math.min((tick - previousTick) * INV_NANOSECOND, 1.0/30.0);
		previousTick = tick;
		entity.minecraftTravel(input, dt);
	}

	@Override
	public void takeKnockback(double strength, double x, double z) {
		// Take kb
		entity.minecraftTakeKnockback(strength, x, z);
	}

	@Override
	public void move(MovementType type, Vec3d delta) {
		entity.move(type, delta);
	}

	@Override
	public Vec3d getLerpedPos(final float tickDelta) {
		return entity.minecraftGetLerpedPos(tickDelta);
	}

	@Override
	public Vec3d getCameraPosVec(final float tickDelta) {
		// Get interpolated offset
		return entity.minecraftGetCameraPosVec(tickDelta);
	}

	@Override
	public void jump() {
		// Nothing :P
	}
}
