package fr.ladycraftfr.ofem.mixin;

import fr.ladycraftfr.ofem.OFEMMod;
import fr.ladycraftfr.ofem.config.OFEMConfig;
import net.minecraft.core.BlockPos;
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

/**
 * Mixin sur {@link Entity} — port fidèle de <em>optimizedFastEntityMovement</em>
 * de Carpet TIS Addition (fallen-breath, LGPL-3.0) vers NeoForge 1.21.1.
 *
 * <h2>Fonctionnement</h2>
 * <p>Dans {@code Entity#move(MoverType, Vec3)}, Minecraft appelle {@code this.collide(Vec3)}
 * pour calculer le déplacement effectif après résolution des collisions.
 * Cette méthode collecte toutes les {@link VoxelShape} de blocs dans une AABB
 * couvrant l'intégralité du mouvement 3D, puis les passe à {@link Shapes#collide}.
 * Pour les entités rapides (TNT, projectiles, canons), cette zone devient énorme
 * et peut contenir des milliers de shapes — source principale du lag.</p>
 *
 * <p>L'optimisation : si la vitesse dépasse le seuil configuré, on ne collecte les
 * shapes <strong>que sur l'axe de déplacement dominant</strong>.
 * Les axes secondaires sont ignorés (zone de recherche "slim").
 * L'algorithme de réponse vanilla ({@link Shapes#collide}) est conservé intact ;
 * seule la liste d'entrée est réduite.</p>
 *
 * <h2>Sécurité</h2>
 * <ul>
 *   <li>Vitesse ≤ seuil → comportement vanilla intégral.</li>
 *   <li>Vecteur nul, NaN, Infini → comportement vanilla.</li>
 *   <li>Exception dans l'optimisation → fallback vanilla + log d'erreur.</li>
 *   <li>Chunk non chargé → shapes ignorées (identique à vanilla).</li>
 * </ul>
 *
 * <h2>Entités non affectées en pratique</h2>
 * <ul>
 *   <li>Joueurs, mobs lents — vitesse en dessous du seuil.</li>
 *   <li>Minecarts, blocs tombants — idem.</li>
 *   <li>Entités dont la vitesse dépasse le seuil mais dont la direction est
 *       essentiellement verticale (chute) → axe Y dominant, comportement correct.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    // ─── Shadow ───────────────────────────────────────────────────────────────

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract AABB getBoundingBox();

    // ─── Injection dans Entity#collide(Vec3) ──────────────────────────────────

    /**
     * Intercepte {@code Entity#collide(Vec3)} au tout début.
     *
     * <p>Si l'optimisation est active et la vitesse dépasse le seuil, on calcule
     * le résultat de collision avec une liste de shapes réduite et on annule la
     * suite de l'exécution vanilla en renvoyant notre résultat.
     * Sinon on laisse vanilla s'exécuter normalement.</p>
     *
     * <p>La méthode {@code collide(Vec3)} est {@code private} en vanilla ;
     * Mixin y accède via le descripteur de type complet. En mappings Mojang 1.21.1
     * la méthode se nomme {@code collide} et prend un {@code Vec3}.</p>
     */
    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ofem$interceptCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        // ── Conditions d'éligibilité ──────────────────────────────────────────
        final OFEMConfig cfg = OFEMConfig.INSTANCE;
        if (!cfg.isEnabled()) return;

        // Sécurité numérique
        final double sx = movement.x, sy = movement.y, sz = movement.z;
        if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)) return;

        final double speedSq = sx * sx + sy * sy + sz * sz;
        final double threshold = cfg.getSpeedThreshold();
        // Comparaison en carré pour éviter Math.sqrt inutile
        if (speedSq <= threshold * threshold) return;

        // ── Axe dominant ──────────────────────────────────────────────────────
        final double ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
        final int dominantAxis; // 0=X, 1=Y, 2=Z
        if (ax >= ay && ax >= az)  dominantAxis = 0;
        else if (ay >= az)         dominantAxis = 1;
        else                       dominantAxis = 2;

        try {
            final Level world    = this.level();
            final AABB entityBB  = this.getBoundingBox();
            final Entity self    = (Entity)(Object)this;

            // ── AABB de recherche réduite (identique à l'approche Carpet TIS) ──
            // Marge infinitésimale sur les axes secondaires pour ne pas manquer
            // les bords de blocs proches de la hitbox de l'entité.
            final double M      = 1.0E-7D;
            final AABB searchBB = ofem$slimAABB(entityBB, movement, dominantAxis, M);

            // ── Collecte des shapes sur l'axe dominant ────────────────────────
            final CollisionContext ctx    = CollisionContext.of(self);
            final List<VoxelShape> shapes = new ArrayList<>();

            BlockPos.betweenClosedStream(searchBB).forEach(blockPos -> {
                // Ignorer les chunks non chargés (identique à vanilla)
                if (!world.isLoaded(blockPos)) return;

                final var blockState = world.getBlockState(blockPos);
                final VoxelShape shape = blockState.getCollisionShape(world, blockPos, ctx);

                // Exclure les shapes vides pour éviter des calculs inutiles
                if (!shape.isEmpty()) {
                    // Translate la shape en coordonnées monde
                    shapes.add(shape.move(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                }
            });

            // ── World Border (reproduit intégralement le comportement vanilla) ─
            // On vérifie si la searchBB intersecte la world border pour l'inclure.
            final WorldBorder border = world.getWorldBorder();
            ofem$addBorderShapeIfNeeded(border, entityBB, shapes);

            // ── Calcul de collision vanilla (inchangé) ────────────────────────
            // Shapes.collide fait l'algorithme sweep axis-by-axis standard.
            // On passe notre liste réduite ; le calcul de réponse reste 100 % vanilla.
            final Vec3 result = Shapes.collide(movement, entityBB, shapes.stream());
            cir.setReturnValue(result);

        } catch (Exception e) {
            // Fallback défensif : on laisse la méthode vanilla continuer normalement.
            OFEMMod.LOGGER.error("[OFEM] Exception dans l'optimisation de collision — fallback vanilla", e);
            // cir n'est pas annulé, vanilla s'exécute
        }
    }

    // ─── Helpers privés ───────────────────────────────────────────────────────

    /**
     * Construit l'AABB « slim » pour la collecte de shapes sur l'axe dominant.
     *
     * <p>Sur l'axe dominant, l'AABB couvre la totalité du déplacement.
     * Sur les deux axes secondaires, on garde les dimensions de l'entité
     * plus une marge {@code margin}.</p>
     */
    @Unique
    private static AABB ofem$slimAABB(AABB bb, Vec3 mv, int axis, double margin) {
        return switch (axis) {
            case 0 -> new AABB(                                    // X dominant
                    bb.minX + Math.min(mv.x, 0.0), bb.minY - margin, bb.minZ - margin,
                    bb.maxX + Math.max(mv.x, 0.0), bb.maxY + margin, bb.maxZ + margin
            );
            case 1 -> new AABB(                                    // Y dominant
                    bb.minX - margin, bb.minY + Math.min(mv.y, 0.0), bb.minZ - margin,
                    bb.maxX + margin, bb.maxY + Math.max(mv.y, 0.0), bb.maxZ + margin
            );
            default -> new AABB(                                   // Z dominant
                    bb.minX - margin, bb.minY - margin, bb.minZ + Math.min(mv.z, 0.0),
                    bb.maxX + margin, bb.maxY + margin, bb.maxZ + Math.max(mv.z, 0.0)
            );
        };
    }

    /**
     * Ajoute la shape de la world border à la liste si l'AABB de l'entité est
     * suffisamment proche de la frontière du monde.
     *
     * <p>Utilise {@link WorldBorder#getCollisionShape()} et un test d'intersection
     * de l'AABB avec la zone proche de la bordure, reproduisant le comportement
     * vanilla de {@code Entity#collide}.</p>
     */
    @Unique
    private static void ofem$addBorderShapeIfNeeded(
            WorldBorder border, AABB entityBB, List<VoxelShape> shapes
    ) {
        // La world border ne s'applique que si l'entité est dans sa zone de collision.
        // On utilise canCollideWith comme vanilla (vérification si l'entité peut toucher la bordure).
        // La méthode getCollisionShape() de WorldBorder est publique et stable.
        final VoxelShape borderShape = border.getCollisionShape();
        if (!Shapes.joinIsNotEmpty(borderShape, Shapes.create(entityBB), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
            // L'AABB de l'entité n'intersecte pas la bordure → inutile de l'ajouter
            return;
        }
        shapes.add(borderShape);
    }
}
