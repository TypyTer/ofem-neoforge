package fr.ladycraftfr.ofem.mixin;

import fr.ladycraftfr.ofem.OFEMMod;
import fr.ladycraftfr.ofem.config.OFEMConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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

    @Shadow public abstract Level level();
    @Shadow public abstract AABB getBoundingBox();

    @Unique
    private static long ofem$optimizedCalls = 0;

    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ofem$interceptCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {

        OFEMConfig cfg = OFEMConfig.INSTANCE;
        if (!cfg.isEnabled()) return;

        double sx = movement.x;
        double sy = movement.y;
        double sz = movement.z;

        if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)) return;

        double speedSq = sx * sx + sy * sy + sz * sz;
        double threshold = cfg.getSpeedThreshold();

        if (speedSq <= threshold * threshold) return;

        ofem$optimizedCalls++;

        if (ofem$optimizedCalls % 2000 == 0) {
            OFEMMod.LOGGER.info("[OFEM] optimized collide calls={}", ofem$optimizedCalls);
        }

        Level world = this.level();
        AABB bb = this.getBoundingBox();
        Entity self = (Entity)(Object)this;

        CollisionContext ctx = CollisionContext.of(self);

        List<VoxelShape> shapes = new ArrayList<>();

        int minX = (int)Math.floor(bb.minX - 1);
        int minY = (int)Math.floor(bb.minY - 1);
        int minZ = (int)Math.floor(bb.minZ - 1);
        int maxX = (int)Math.floor(bb.maxX + 1);
        int maxY = (int)Math.floor(bb.maxY + 1);
        int maxZ = (int)Math.floor(bb.maxZ + 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {

                    var pos = new net.minecraft.core.BlockPos(x, y, z);

                    if (!world.isLoaded(pos)) continue;

                    var state = world.getBlockState(pos);

                    VoxelShape shape = state.getCollisionShape(world, pos, ctx);

                    if (!shape.isEmpty()) {
                        shapes.add(shape.move(x, y, z));
                    }
                }
            }
        }

        WorldBorder border = world.getWorldBorder();
        VoxelShape borderShape = border.getCollisionShape();

        if (!borderShape.isEmpty()) {
            shapes.add(borderShape);
        }

        double mx = sx, my = sy, mz = sz;

        for (VoxelShape shape : shapes) {
            mx = shape.collide(net.minecraft.core.Direction.Axis.X, bb, mx);
        }

        AABB bbX = bb.move(mx, 0, 0);

        for (VoxelShape shape : shapes) {
            my = shape.collide(net.minecraft.core.Direction.Axis.Y, bbX, my);
        }

        AABB bbXY = bbX.move(0, my, 0);

        for (VoxelShape shape : shapes) {
            mz = shape.collide(net.minecraft.core.Direction.Axis.Z, bbXY, mz);
        }

        cir.setReturnValue(new Vec3(mx, my, mz));
    }
}
