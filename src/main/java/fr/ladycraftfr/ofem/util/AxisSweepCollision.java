package com.typyter.ofem.util;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the single large getBlockCollisions sweep in Entity.collide() with
 * three narrow per-axis sweeps, preserving vanilla collision results exactly.
 *
 * <h2>Why this is equivalent to vanilla</h2>
 * Vanilla Entity.collide() does:
 * <pre>
 *   shapes = level.getBlockCollisions(entity, box.expandTowards(movement))
 *   return Collision.findFreeSpace(shapes, movement, box)
 * </pre>
 * Collision.findFreeSpace decomposes movement into Y → X → Z sequential passes.
 * In each pass, it calls {@code VoxelShape.collide(Axis, box, delta)}.
 * That method only cares whether the shape's AABB overlaps the current sweep
 * along the given axis — shapes that don't overlap return delta unchanged.
 * Therefore, supplying only the shapes that are reachable from the entity's
 * bounding box along the current axis produces the same numeric result as
 * supplying all shapes from the huge union box.
 *
 * <h2>Why TNT cannons were broken before</h2>
 * The previous (broken) implementation intercepted the single getBlockCollisions
 * call and returned shapes from only one axis's sweep box, then passed those
 * shapes to vanilla's Collision.findFreeSpace. The other two axes received no
 * shapes, so block walls along those axes were invisible to the collision system.
 * TNT flew through cannon barrel walls and detonated inside them.
 *
 * <h2>Safety conditions</h2>
 * <ul>
 *   <li>Only activated when movement > 1.0 on at least one axis (normal
 *       players/mobs never trigger this).</li>
 *   <li>AABB instances are immutable in vanilla (AABB.move returns a new AABB);
 *       we never mutate the entity's bounding box.</li>
 *   <li>Level.getBlockCollisions is called with the standard API; NeoForge hooks
 *       fire identically to vanilla.</li>
 *   <li>Axis decomposition order (Y → then X/Z by magnitude) matches vanilla
 *       Collision.findFreeSpace exactly, including the |dx| >= |dz| tiebreak.</li>
 * </ul>
 */
public final class AxisSweepCollision {

    private AxisSweepCollision() {}

    /**
     * Returns true when the per-axis optimization is worth applying.
     * Below 1.0 blocks per tick the union box is small; the overhead of three
     * separate getBlockCollisions calls exceeds the savings.
     */
    public static boolean shouldOptimize(Vec3 movement) {
        return Math.abs(movement.x) > 1.0
                || Math.abs(movement.y) > 1.0
                || Math.abs(movement.z) > 1.0;
    }

    /**
     * Computes the collision-adjusted movement vector using three separate
     * per-axis block-collision sweeps instead of one large union sweep.
     *
     * @param level    the current level (non-null)
     * @param entity   the moving entity (used as collision context)
     * @param startBox the entity's current bounding box (before movement)
     * @param movement the requested movement vector
     * @return the adjusted movement vector after block collisions
     */
    public static Vec3 collideAxesSeparately(
            Level level,
            Entity entity,
            AABB startBox,
            Vec3 movement) {

        double dx = movement.x;
        double dy = movement.y;
        double dz = movement.z;

        // ── Pass 1: Y axis (always first — matches Collision.findFreeSpace) ──
        if (dy != 0.0) {
            // Sweep box: entity footprint (XZ), expanded only in Y direction.
            // Thin in X and Z → skips all blocks not in the vertical path.
            AABB sweepY = startBox.expandTowards(0.0, dy, 0.0);
            List<VoxelShape> shapesY = collect(level, entity, sweepY);
            for (VoxelShape shape : shapesY) {
                dy = shape.collide(Direction.Axis.Y, startBox, dy);
            }
            startBox = startBox.move(0.0, dy, 0.0);  // advance box by resolved dy
        }

        // ── Pass 2 & 3: X and Z, ordered by magnitude (vanilla tiebreak) ──
        // Vanilla Collision.findFreeSpace: if |dx| >= |dz|, X resolves first.
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);

        if (xFirst && dx != 0.0) {
            AABB sweepX = startBox.expandTowards(dx, 0.0, 0.0);
            List<VoxelShape> shapesX = collect(level, entity, sweepX);
            for (VoxelShape shape : shapesX) {
                dx = shape.collide(Direction.Axis.X, startBox, dx);
            }
            startBox = startBox.move(dx, 0.0, 0.0);
        }

        if (dz != 0.0) {
            AABB sweepZ = startBox.expandTowards(0.0, 0.0, dz);
            List<VoxelShape> shapesZ = collect(level, entity, sweepZ);
            for (VoxelShape shape : shapesZ) {
                dz = shape.collide(Direction.Axis.Z, startBox, dz);
            }
            // Only advance startBox for Z if X hasn't gone yet
            // (mirrors vanilla's conditional box advancement)
            if (!xFirst) {
                startBox = startBox.move(0.0, 0.0, dz);
            }
        }

        // Second horizontal pass (when Z went first, X goes last)
        if (!xFirst && dx != 0.0) {
            AABB sweepX = startBox.expandTowards(dx, 0.0, 0.0);
            List<VoxelShape> shapesX = collect(level, entity, sweepX);
            for (VoxelShape shape : shapesX) {
                dx = shape.collide(Direction.Axis.X, startBox, dx);
            }
            // No need to advance startBox; this is the final pass
        }

        return new Vec3(dx, dy, dz);
    }

    /**
     * Collects all block VoxelShapes that overlap {@code sweepBox} into a list.
     * Uses the same Level.getBlockCollisions API as vanilla so that NeoForge
     * collision events fire identically.
     */
    private static List<VoxelShape> collect(Level level, Entity entity, AABB sweepBox) {
        ArrayList<VoxelShape> list = new ArrayList<>();
        level.getBlockCollisions(entity, sweepBox).forEach(list::add);
        return list;
    }
}
