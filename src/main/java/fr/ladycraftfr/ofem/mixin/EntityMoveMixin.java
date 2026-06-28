package fr.ladycraftfr.ofem.mixin;

import fr.ladycraftfr.ofem.util.AxisSweepCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimizes Entity.collide(Vec3) for fast-moving entities.
 *
 * <h2>Strategy</h2>
 * Inject at HEAD of Entity.collide(), cancel early, and return the result of
 * AxisSweepCollision.collideAxesSeparately() — which replaces the single large
 * getBlockCollisions sweep with three narrow per-axis sweeps.
 *
 * <h2>Why @Inject at HEAD rather than @Redirect on getBlockCollisions</h2>
 * The broken version used @Redirect to intercept the single getBlockCollisions
 * call inside collide() and returned shapes from only one narrow box. Vanilla's
 * Collision.findFreeSpace then ran with an incomplete shape set.
 *
 * The correct approach is to bypass Collision.findFreeSpace entirely and perform
 * the three-pass decomposition ourselves, each pass with its own narrow sweep.
 * @Inject at HEAD with cancellable = true is the cleanest way to do this without
 * depending on bytecode-level targeting of internal vanilla code.
 *
 * <h2>When the optimization does NOT fire</h2>
 * - Movement ≤ 1.0 blocks on all axes (normal players, mobs, items on ground).
 * - Entity has no level reference (being constructed or removed).
 * - Entity is dead/removed.
 *
 * In all these cases the method returns normally and vanilla runs unmodified.
 *
 * <h2>TNT cannon correctness</h2>
 * TNT is fired at ~7–20 blocks/tick depending on cannon design.
 * With the old broken code, only the dominant-axis shapes were queried,
 * so the cannon barrel's walls (on orthogonal axes) were invisible — the TNT
 * flew through them and detonated inside the structure.
 *
 * With this fix:
 * - Y sweep: finds the floor/ceiling (stops vertical component correctly).
 * - X sweep: finds the X-axis barrel walls.
 * - Z sweep: finds the Z-axis barrel walls.
 * All three axes' blocks are correctly considered. The TNT exits the barrel
 * at the open end and detonates at the intended location.
 */
@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    /**
     * Intercepts Entity.collide(Vec3) to apply the per-axis sweep optimization.
     *
     * The Mixin descriptor must match the NeoForge 1.21.1 obfuscated name.
     * In NeoForge 1.21.1 with Mojang mappings the method is:
     *   net.minecraft.world.entity.Entity.collide(Lnet/minecraft/world/phys/Vec3;)
     *                                             Lnet/minecraft/world/phys/Vec3;
     * which in intermediary is the same (NeoForge uses Mojang mappings at runtime).
     */
    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ofem_collide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        // Fast exit for normal-speed entities: do not change their behavior at all.
        if (!AxisSweepCollision.shouldOptimize(movement)) {
            return;
        }

        Entity self = (Entity) (Object) this;

        // Guard: level can be null during entity construction,
        // and isAlive() can be false during removal.
        if (self.level() == null || !self.isAlive()) {
            return;
        }

        AABB entityBox = self.getBoundingBox();

        Vec3 result = AxisSweepCollision.collideAxesSeparately(
                self.level(), self, entityBox, movement);

        // Entity.collide()'s only job is to return an adjusted Vec3.
        // All side-effect logic (step sounds, onGround, portal checks) lives
        // in Entity.move() and is NOT affected by this interception.
        cir.setReturnValue(result);
    }
}
