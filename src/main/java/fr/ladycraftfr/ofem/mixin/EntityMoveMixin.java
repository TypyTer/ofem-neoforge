package fr.ladycraftfr.ofem.mixin;

import fr.ladycraftfr.ofem.OFEMMod;
import fr.ladycraftfr.ofem.config.OFEMConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract AABB getBoundingBox();

    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ofem$interceptCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        final OFEMConfig cfg = OFEMConfig.INSTANCE;
        if (!cfg.isEnabled()) return;

        final double sx = movement.x, sy = movement.y, sz = movement.z;
        if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)) return;

        final double speedSq = sx * sx + sy * sy + sz * sz;
        final double threshold = cfg.getSpeedThreshold();
        if (speedSq <= threshold * threshold) return;

        final double ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
        final int dominantAxis;
        if (ax >= ay && ax >= az)  dominantAxis = 0;
        else if (ay >= az)         dominantAxis = 1;
        else                       dominantAxis = 2;

        try {
            final Level world   = this.level();
            final AABB entityBB = this.getBoundingBox();
            final Entity self   = (Entity)(Object)this;

            final double M      = 1.0E-7D;
            final AABB searchBB = ofem$slimAABB(entityBB, movement, dominantAxis, M);

            final CollisionContext ctx    = CollisionContext.of(self);
            final List<VoxelShape> shapes = new ArrayList<>();

            BlockPos.betweenClosedStream(searchBB).forEach(blockPos -> {
                if (!world.isLoaded(blockPos)) return;
                var blockState = world.getBlockState(blockPos);
                var shape = blockState.getCollisionShape(world, blockPos, ctx);
                if (!shape.isEmpty()) {
                    shapes.add(shape.move(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                }
            });

            final WorldBorder border = world.getWorldBorder();
            final VoxelShape borderShape = border.getCollisionShape();
            if (!borderShape.isEmpty()) {
                shapes.add(borderShape);
            }

            // Calcul axis-by-axis comme vanilla, mais avec notre liste réduite
            Vec3 result = movement;
            for (VoxelShape shape : shapes) {
                result = shape.collide(Direction.Axis.X, entityBB.move(
                        result.x - movement.x, result.y - movement.y, result.z - movement.z), result);
            }
            // Recalcul simplifié axe par axe
            double mx = movement.x, my = movement.y, mz = movement.z;
            for (VoxelShape shape : shapes) {
                mx = shape.collide(Direction.Axis.X, entityBB, mx);
            }
            AABB bbAfterX = entityBB.move(mx, 0, 0);
            for (VoxelShape shape : shapes) {
                my = shape.collide(Direction.Axis.Y, bbAfterX, my);
            }
            AABB bbAfterXY = bbAfterX.move(0, my, 0);
            for (VoxelShape shape : shapes) {
                mz = shape.collide(Direction.Axis.Z, bbAfterXY, mz);
            }

            cir.setReturnValue(new Vec3(mx, my, mz));

        } catch (Exception e) {
            OFEMMod.LOGGER.error("[OFEM] Erreur — fallback vanilla", e);
        }
    }

    @Unique
    private static AABB ofem$slimAABB(AABB bb, Vec3 mv, int axis, double margin) {
        return switch (axis) {
            case 0 -> new AABB(
                    bb.minX + Math.min(mv.x, 0.0), bb.minY - margin, bb.minZ - margin,
                    bb.maxX + Math.max(mv.x, 0.0), bb.maxY + margin, bb.maxZ + margin
            );
            case 1 -> new AABB(
                    bb.minX - margin, bb.minY + Math.min(mv.y, 0.0), bb.minZ - margin,
                    bb.maxX + margin, bb.maxY + Math.max(mv.y, 0.0), bb.maxZ + margin
            );
            default -> new AABB(
                    bb.minX - margin, bb.minY - margin, bb.minZ + Math.min(mv.z, 0.0),
                    bb.maxX + margin, bb.maxY + margin, bb.maxZ + Math.max(mv.z, 0.0)
            );
        };
    }
}
