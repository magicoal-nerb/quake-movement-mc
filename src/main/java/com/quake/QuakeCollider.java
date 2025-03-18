package com.quake;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;

// Mostly because I might add continuous collision in the future
@SuppressWarnings("unused")
public class QuakeCollider {
    private static final Vec3d X_NEG = new Vec3d(-1.0, 0.0, 0.0);
    private static final Vec3d Y_NEG = new Vec3d(0.0, -1.0, 0.0);
    private static final Vec3d Z_NEG = new Vec3d(0.0, 0.0, -1.0);

    private static final Vec3d X_POS = new Vec3d(1.0, 0.0, 0.0);
    private static final Vec3d Y_POS = new Vec3d(0.0, 1.0, 0.0);
    private static final Vec3d Z_POS = new Vec3d(0.0, 0.0, 1.0);

    private static final Vec3d[] STEPS = new Vec3d[] {
        new Vec3d(1.0, 0.0, 1.0),
        new Vec3d(0.0, 1.0, 0.0),
    };

    protected static ContinuousContact continuous = new ContinuousContact();
    protected static DiscreteContact discrete = new DiscreteContact();
    protected static AABB localBox = new AABB();

    private static class AABB {
        public Vec3d min;
        public Vec3d max;
    };

    private static class DiscreteContact {
        public boolean active = false;
        public Vec3d normal;
        public Vec3d mtv;
    };

    private static class ContinuousContact {
        public boolean active = false;
        public Vec3d normal;
        public double t;
    };

    private static final boolean doesAABBCollide(
        final Vec3d min,
        final Vec3d max,
        final Vec3d position
    ) {
        // Within AABB?
        return min.x < position.x && position.x < max.x
            && min.y < position.y && position.y < max.y
            && min.z < position.z && position.z < max.z;
    }

    private static void generateSpeculativeContact(
        final AABB box,
        final Vec3d halfSize,
        final Vec3d origin,
        final Vec3d invDir
    ) {
        // There's speculative contacts, but I don't think that's
        // actually necessary here.
        Vec3d min = new Vec3d(box.min.x - halfSize.x, box.min.y - halfSize.y, box.min.z - halfSize.z);
        Vec3d max = new Vec3d(box.max.x + halfSize.x, box.max.y + halfSize.y, box.max.z + halfSize.z);

        Vec3d v0 = min.subtract(origin).multiply(invDir);
        Vec3d v1 = max.subtract(origin).multiply(invDir);
        Vec3d rMin = new Vec3d(Math.min(v0.x, v1.x), Math.min(v0.y, v1.y), Math.min(v0.z, v1.z));
        Vec3d rMax = new Vec3d(Math.max(v0.x, v1.x), Math.max(v0.y, v1.y), Math.max(v0.z, v1.z));

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

    private static Vec3d projectPlane(Vec3d p, Vec3d n) {
        // Want: (p + n*lambda).n = 0, lambda>0, |n|=1
        // lambda = -p.n / n.n
        // lambda = -p.n
        double lambda = Math.min(p.dotProduct(n), 0.0);
        return p.subtract(n.multiply(lambda));
    }

    private static void generateDiscreteContact(
        final AABB box,
        final Vec3d halfSize,
        final Vec3d origin,
        DiscreteContact info
    ) {
        // Expand box boundaries.
        Vec3d min = box.min.subtract(halfSize);
        Vec3d max = box.max.add(halfSize);

        if(doesAABBCollide(min, max, origin)) {
            // supp(box, origin - box.center)
            final Vec3d center = min.add(max).multiply(0.5);
            final Vec3d offset = new Vec3d(
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

    public static final Vec3d quakeGetWall(
        final Entity entity,
        final Vec3d wishDir
    ) {
        final Box box = entity.getBoundingBox();
        final Iterator<VoxelShape> list = entity
            .getWorld()
            .getBlockCollisions(entity, box.expand(1.0))
            .iterator();

        final Vec3d origin = box.getCenter();

        Vec3d bestNormal = Vec3d.ZERO;
        double bestDot = 1.0;
        while(list.hasNext()) {
            final VoxelShape shape = list.next();
            final Box shapeBox = shape.getBoundingBox();
            final Vec3d center = shapeBox.getCenter();

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
        final Box box = entity.getBoundingBox();
        final Vec3d center = box.getCenter();

        final Vec3d plane = new Vec3d(center.x, 0, center.z);
        final Vec3d halfSize = new Vec3d(
            (box.maxX - box.minX) * 0.5 - 1e-1,
            (box.maxY - box.minY) * 0.5 - 1e-1,
            (box.maxZ - box.minZ) * 0.5 - 1e-1
        );

        final Iterator<VoxelShape> voxels = entity
            .getWorld()
            .getBlockCollisions(entity, box.expand(1.0))
            .iterator();

        AtomicReference<Double> bestCost = new AtomicReference<Double>(Double.MAX_VALUE);
        AtomicReference<Vec3d> bestConstraint = new AtomicReference<Vec3d>(plane);

        // Generate constraints
        while(voxels.hasNext()) {
            final VoxelShape shape = voxels.next();
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                localBox.min = new Vec3d(minX, minY, minZ);
                localBox.max = new Vec3d(maxX, maxY, maxZ);

                if(maxY > box.minY + 1e-2) {
                    // Ceiling, don't use this.
                    return;
                }

                final Vec3d constraint = new Vec3d(
                    Math.max(Math.min(plane.x, maxX + halfSize.x), minX - halfSize.x),
                    Math.min(maxY - box.minY + 1e-2, 0.0),
                    Math.max(Math.min(plane.z, maxZ + halfSize.z), minZ - halfSize.z)
                );

                final double cost = constraint.subtract(plane).lengthSquared();
                if(cost < bestCost.get()) {
                    // Min |c - pos|
                    // Gives us a good enough constraint
                    bestConstraint.set(constraint);
                    bestCost.set(cost);
                }
            });
        }

        // Apply constraint
        final Vec3d position = bestConstraint.get();
        final Vec3d velocity = entity.getVelocity().multiply(
            (position.x == plane.x ? 1.0 : 0.0),
            0.0,
            (position.z == plane.z ? 1.0 : 0.0)
        );

        entity.setPosition(position.x, entity.getPos().y + position.y, position.z);
        entity.setVelocity(velocity);
    }

    private static ArrayList<Box> quakeGetCollidingBoxes(
        final Entity entity,
        final Vec3d offset
    ) {
        // Create boxes
        final World world = entity.getWorld();
        final Box box = entity.getBoundingBox();

        ArrayList<Box> boxes = new ArrayList<Box>();
        for(VoxelShape shape: world.getBlockCollisions(entity, box.stretch(offset))) {
            boxes.addAll(shape.getBoundingBoxes());
        }

        for(VoxelShape shape: world.getEntityCollisions(entity, box.stretch(offset))) {
            boxes.add(shape.getBoundingBox());
        }

        return boxes;
    }

	public static void quakeCollide(
        final Entity entity,
        final boolean wasOnGround,
        Vec3d offset,
        AtomicReference<Vec3d> camera
    ) {
        // Fields
        final Box box = entity.getBoundingBox();
        final Vec3d halfSize = new Vec3d(
            (box.maxX - box.minX) * 0.5,
            (box.maxY - box.minY) * 0.5,
            (box.maxZ - box.minZ) * 0.5
        );

        // Do discrete step, because Minecraft will just
        // teleport you back anyways lol
        AtomicReference<Vec3d> velocity = new AtomicReference<Vec3d>(entity.getVelocity());
        AtomicReference<Vec3d> position = new AtomicReference<Vec3d>(box.getCenter());
        double prevY = position.get().y + offset.y;

        // Generate contacts
        final ArrayList<Box> boxes = quakeGetCollidingBoxes(entity, offset);
        final double stepHeight = entity.getStepHeight();

        AtomicReference<Double> desiredStep = new AtomicReference<Double>(stepHeight);
        for(Vec3d step : STEPS) {
            // Do discrete collision checks. Minecraft should
            // check for us anyways lol.
            position.set(position.get().add(offset.multiply(step)));
            for(Box shape: boxes) {
                localBox.min = new Vec3d(shape.minX, shape.minY, shape.minZ);
                localBox.max = new Vec3d(shape.maxX, shape.maxY, shape.maxZ);

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
                        final Vec3d normal = discrete.normal.multiply(step);
                        desiredStep.set(0.0);
                        position.set(position.get().add(discrete.mtv).add(normal.multiply(1e-6)));
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
        entity.setVelocity(velocity.get());
        entity.setPosition(position.get().add(0.0, -halfSize.y, 0.0));
    }
}
