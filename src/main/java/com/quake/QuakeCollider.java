package com.quake;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// Mostly because I might add continuous collision in the future
@SuppressWarnings("unused")
public class QuakeCollider {
    private static final Vec3 X_NEG = new Vec3(-1.0, 0.0, 0.0);
    private static final Vec3 Y_NEG = new Vec3(0.0, -1.0, 0.0);
    private static final Vec3 Z_NEG = new Vec3(0.0, 0.0, -1.0);

    private static final Vec3 X_POS = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 Y_POS = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 Z_POS = new Vec3(0.0, 0.0, 1.0);

    private static final Vec3[] STEPS = new Vec3[] {
        new Vec3(1.0, 0.0, 1.0),
        new Vec3(0.0, 1.0, 0.0),
    };

    protected static ContinuousContact continuous = new ContinuousContact();
    protected static DiscreteContact discrete = new DiscreteContact();
    protected static BoundingBox localBox = new BoundingBox();

    private static class BoundingBox {
        public Vec3 min;
        public Vec3 max;
    };

    private static class DiscreteContact {
        public boolean active = false;
        public Vec3 normal;
        public Vec3 mtv;
    };

    private static class ContinuousContact {
        public boolean active = false;
        public Vec3 normal;
        public double t;
    };

    private static final boolean doesAABBCollide(
        final Vec3 min,
        final Vec3 max,
        final Vec3 position
    ) {
        // Within AABB?
        return min.x < position.x && position.x < max.x
            && min.y < position.y && position.y < max.y
            && min.z < position.z && position.z < max.z;
    }

    private static void generateSpeculativeContact(
        final BoundingBox box,
        final Vec3 halfSize,
        final Vec3 origin,
        final Vec3 invDir
    ) {
        // There's speculative contacts, but I don't think that's
        // actually necessary here.
        Vec3 min = new Vec3(box.min.x - halfSize.x, box.min.y - halfSize.y, box.min.z - halfSize.z);
        Vec3 max = new Vec3(box.max.x + halfSize.x, box.max.y + halfSize.y, box.max.z + halfSize.z);

        Vec3 v0 = min.subtract(origin).multiply(invDir);
        Vec3 v1 = max.subtract(origin).multiply(invDir);
        Vec3 rMin = new Vec3(Math.min(v0.x, v1.x), Math.min(v0.y, v1.y), Math.min(v0.z, v1.z));
        Vec3 rMax = new Vec3(Math.max(v0.x, v1.x), Math.max(v0.y, v1.y), Math.max(v0.z, v1.z));

        double t0 = Math.max(Math.max(rMin.x, rMin.y), rMin.z);
        double t1 = Math.min(Math.min(rMax.x, rMax.y), rMax.z);
        if(t0 <= t1 && 0.0 <= t0 && t0 <= 1.0){
            continuous.active = true;
            if(rMin.x > rMin.y && rMin.x > rMin.z) {
                continuous.normal = 0.0 < invDir.x ? X_NEG : X_POS;
            } else if(rMin.y > rMin.x && rMin.y > rMin.z) {
                continuous.normal = 0.0 < invDir.y ? Y_NEG : Y_POS;
            } else {
                continuous.normal = 0.0 < invDir.z ? Z_NEG : Z_POS;
            }

            continuous.t = t0;
        } else {
            continuous.active = false;
        }
    }

    private static Vec3 projectPlane(Vec3 p, Vec3 n) {
        // Want: (p + n*lambda).n = 0, lambda>0, |n|=1
        // lambda = -p.n / n.n
        // lambda = -p.n
        double lambda = Math.min(p.dot(n), 0.0);
        return p.subtract(n.scale(lambda));
    }

    private static void generateDiscreteContact(
        final BoundingBox box,
        final Vec3 halfSize,
        final Vec3 origin,
        DiscreteContact info
    ) {
        // Expand box boundaries.
        Vec3 min = box.min.subtract(halfSize);
        Vec3 max = box.max.add(halfSize);

        if(doesAABBCollide(min, max, origin)) {
            // supp(box, origin - box.center)
            final Vec3 center = min.add(max).scale(0.5);
            final Vec3 offset = new Vec3(
                Math.max(Math.min(origin.x - min.x, max.x - origin.x), 0.0),
                Math.max(Math.min(origin.y - min.y, max.y - origin.y), 0.0),
                Math.max(Math.min(origin.z - min.z, max.z - origin.z), 0.0)
            );

            // Generate normal from the MTV
            info.active = true;

            if(offset.x < offset.y && offset.x < offset.z) {
                info.normal = center.x < origin.x ? X_POS : X_NEG;
                info.mtv = offset.multiply(info.normal);
            } else if(offset.y < offset.x && offset.y < offset.z) {
                info.normal = center.y < origin.y ? Y_POS : Y_NEG;
                info.mtv = offset.multiply(info.normal);
            } else {
                info.normal = center.z < origin.z ? Z_POS : Z_NEG;
                info.mtv = offset.multiply(info.normal);
            }
        } else {
            info.active = false;
        }
    }

    public static final Vec3 quakeGetWall(
        final Entity entity,
        final Vec3 wishDir
    ) {
        final AABB box = entity.getBoundingBox();
        final Iterator<VoxelShape> list = entity
            .level()
            .getBlockCollisions(entity, box.inflate(1.0))
            .iterator();

        final Vec3 origin = box.getCenter();

        Vec3 bestNormal = Vec3.ZERO;
        double bestDot = 1.0;
        while(list.hasNext()) {
            final VoxelShape shape = list.next();
            if(shape.isEmpty()) {
                // bounds() throws on empty shapes
                continue;
            }

            final AABB shapeBox = shape.bounds();
            final Vec3 center = shapeBox.getCenter();

            final double dx = Math.max(Math.min(origin.x - shapeBox.minX, shapeBox.maxX - origin.x), 0.0);
            final double dz = Math.max(Math.min(origin.z - shapeBox.minZ, shapeBox.maxZ - origin.z), 0.0);
            if(dx < dz) {
                if(dx < bestDot) {
                    // X norm
                    bestNormal = center.x < origin.x ? X_POS : X_NEG;
                    bestDot = dx;
                }
            } else if(dz < bestDot) {
                // Z norm
                bestNormal = center.z < origin.z ? Z_POS : Z_NEG;
                bestDot = dz;
            }
        }

        return bestNormal;
    }

    public static void quakeSneak(
        final Entity entity
    ) {
        // Fields
        final AABB box = entity.getBoundingBox();
        final Vec3 center = box.getCenter();

        final Vec3 plane = new Vec3(center.x, 0, center.z);
        final Vec3 halfSize = new Vec3(
            (box.maxX - box.minX) * 0.5 - 1e-1,
            (box.maxY - box.minY) * 0.5 - 1e-1,
            (box.maxZ - box.minZ) * 0.5 - 1e-1
        );

        final Iterator<VoxelShape> voxels = entity
            .level()
            .getBlockCollisions(entity, box.inflate(0.5))
            .iterator();

        AtomicReference<Double> bestCost = new AtomicReference<Double>(Double.MAX_VALUE);
        AtomicReference<Vec3> bestConstraint = new AtomicReference<Vec3>(plane);

        // Generate constraints
        while(voxels.hasNext()) {
            final VoxelShape shape = voxels.next();
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                localBox.min = new Vec3(minX, minY, minZ);
                localBox.max = new Vec3(maxX, maxY, maxZ);

                if(maxY > box.minY + 1e-2) {
                    // Ceiling, don't use this.
                    return;
                }

                final Vec3 constraint = new Vec3(
                    Math.max(Math.min(plane.x, maxX + halfSize.x), minX - halfSize.x),
                    Math.min(maxY - box.minY + 1e-2, 0.0),
                    Math.max(Math.min(plane.z, maxZ + halfSize.z), minZ - halfSize.z)
                );

                final double cost = constraint.subtract(plane).lengthSqr();
                if(cost < bestCost.get()) {
                    // Min |c - pos|
                    // Gives us a good enough constraint
                    bestConstraint.set(constraint);
                    bestCost.set(cost);
                }
            });
        }

        // Apply constraint
        final Vec3 position = bestConstraint.get();
        final Vec3 velocity = entity.getDeltaMovement().multiply(
            (position.x == plane.x ? 1.0 : 0.0),
            0.0,
            (position.z == plane.z ? 1.0 : 0.0)
        );

        entity.setPos(position.x, entity.position().y + position.y, position.z);
        entity.setDeltaMovement(velocity);
    }

    private static ArrayList<AABB> quakeGetCollidingBoxes(
        final Entity entity,
        final Vec3 offset
    ) {
        // Create boxes
        final Level level = entity.level();
        final AABB box = entity.getBoundingBox();
        final AABB swept = box.expandTowards(offset);

        ArrayList<AABB> boxes = new ArrayList<AABB>();
        for(VoxelShape shape : level.getBlockCollisions(entity, swept)) {
            boxes.addAll(shape.toAabbs());
        }

        for(VoxelShape shape : level.getEntityCollisions(entity, swept)) {
            if(!shape.isEmpty()) {
                boxes.add(shape.bounds());
            }
        }

        return boxes;
    }

    public static void quakeCollide(
        final Entity entity,
        final boolean wasOnGround,
        Vec3 offset,
        AtomicReference<Vec3> camera
    ) {
        // Fields
        final AABB box = entity.getBoundingBox();
        final Vec3 halfSize = new Vec3(
            (box.maxX - box.minX) * 0.5,
            (box.maxY - box.minY) * 0.5,
            (box.maxZ - box.minZ) * 0.5
        );

        // Do discrete step, because Minecraft will just
        // teleport you back anyways lol
        AtomicReference<Vec3> velocity = new AtomicReference<Vec3>(entity.getDeltaMovement());
        AtomicReference<Vec3> position = new AtomicReference<Vec3>(box.getCenter());
        double prevY = position.get().y + offset.y;

        // Generate contacts
        final ArrayList<AABB> boxes = quakeGetCollidingBoxes(entity, offset);
        final double stepHeight = entity.maxUpStep();

        AtomicReference<Double> desiredStep = new AtomicReference<Double>(stepHeight);
        for(Vec3 step : STEPS) {
            // Do discrete collision checks. Minecraft should
            // check for us anyways lol.
            position.set(position.get().add(offset.multiply(step)));
            for(AABB shape : boxes) {
                localBox.min = new Vec3(shape.minX, shape.minY, shape.minZ);
                localBox.max = new Vec3(shape.maxX, shape.maxY, shape.maxZ);

                generateDiscreteContact(
                    localBox,
                    halfSize,
                    position.get(),
                    discrete
                );

                if(discrete.active) {
                    final double floorY = position.get().y - halfSize.y + stepHeight;
                    if(shape.maxY < floorY && wasOnGround) {
                        // Case 1: ascending/descending an AABB
                        // p += [0, maxY - (floorY - stepHeight), 0]
                        desiredStep.set(Math.min(desiredStep.get(), shape.maxY - floorY + stepHeight));
                    } else {
                        // Case 2: colliding with AABB
                        // v -= min(dot(v, n), 0.0) * n
                        // p += mtv + n*1e-6
                        final Vec3 normal = discrete.normal.multiply(step);
                        desiredStep.set(0.0);
                        position.set(position.get().add(discrete.mtv).add(normal.scale(1e-6)));
                        velocity.set(projectPlane(velocity.get(), normal));
                    }
                }
            }
        }

        if(desiredStep.get() != stepHeight) {
            // Do step please
            final double step = desiredStep.get();
            camera.set(camera.get().subtract(0.0, step, 0.0));
            position.set(position.get().add(0.0, step, 0.0));
        }

        // Finalize state
        entity.setOnGround(prevY < position.get().y);
        entity.setDeltaMovement(velocity.get());
        entity.setPos(position.get().add(0.0, -halfSize.y, 0.0));
    }
}