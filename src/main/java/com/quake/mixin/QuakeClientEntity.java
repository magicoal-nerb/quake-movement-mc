package com.quake.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import com.quake.QuakeEntity;
import com.quake.QuakePlayer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

@Mixin(value = LocalPlayer.class, priority = 1)
public abstract class QuakeClientEntity extends AbstractClientPlayer implements QuakePlayer {
    @Unique private static final float INV_NANOSECOND = 1.0f / 1000000000.0f;
    @Unique private QuakeEntity quake$entity;
    @Unique private long quake$previousTick;

    public QuakeClientEntity(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void quake$init(CallbackInfo ci) {
        quake$entity = new QuakeEntity((LocalPlayer)(Object)this);
    }

    @Override
    public QuakeEntity quake$get() {
        return quake$entity;
    }

    @Unique
    private boolean quake$active() {
        // Some of these get called from the super constructor,
        // before our entity exists.
        return quake$entity != null && quake$entity.quakeEnabled();
    }

    @Override
    public void setOnGround(boolean x) {
        // Set on ground if enabled or something
        if(quake$active()) {
            quake$entity.minecraftSetOnGround(x);
        } else {
            super.setOnGround(x);
        }
    }

    @Override
    public void travel(final Vec3 input) {
        // Update timer
        if(quake$active()) {
            final long tick = System.nanoTime();
            double dt = Math.min((tick - quake$previousTick) * INV_NANOSECOND, 1.0/30.0);
            quake$previousTick = tick;

            super.setOnGround(true);
            quake$entity.minecraftTravel(input, dt);
        } else {
            super.travel(input);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Take KB
        if(quake$active()) {
            quake$entity.minecraftTakeKnockback(strength, x, z);
        } else {
            super.knockback(strength, x, z);
        }
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        if(quake$active()) {
            quake$entity.minecraftMove(type, delta);
        } else {
            super.move(type, delta);
        }
    }

    @Override
    public void jumpFromGround() {
        // Nothing :P
        if(!quake$active()) {
            super.jumpFromGround();
        }
    }
}