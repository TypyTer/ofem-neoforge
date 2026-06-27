package fr.ladycraftfr.ofem.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration du mod OptimizedFastEntityMovement.
 *
 * <p>Deux paramètres sont exposés :</p>
 * <ul>
 *   <li>{@code optimizedFastEntityMovement} – active ou non l'optimisation.</li>
 *   <li>{@code speedThreshold} – seuil en blocs/tick à partir duquel l'optimisation s'applique.</li>
 * </ul>
 *
 * <p>Port fidèle de la règle Carpet TIS Addition du même nom.</p>
 */
public class OFEMConfig {

    // ─── Constantes par défaut ────────────────────────────────────────────────
    /**
     * Seuil par défaut : 0,5 bloc/tick ≈ 10 m/s.
     * Dans Carpet TIS Addition la règle s'applique à partir d'une vitesse
     * « fast » ; 0,5 est une valeur conservative qui préserve le comportement
     * vanilla pour les entités normales et accélère le calcul uniquement pour
     * les projectiles/TNT rapidement lancés.
     */
    public static final double DEFAULT_SPEED_THRESHOLD = 0.5D;

    // ─── Holder de l'instance ─────────────────────────────────────────────────
    public static final OFEMConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        final Pair<OFEMConfig, ModConfigSpec> specPair =
                new ModConfigSpec.Builder().configure(OFEMConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC     = specPair.getRight();
    }

    // ─── Valeurs configurables ────────────────────────────────────────────────
    private final ModConfigSpec.BooleanValue optimizedFastEntityMovement;
    private final ModConfigSpec.DoubleValue  speedThreshold;

    private OFEMConfig(ModConfigSpec.Builder builder) {
        builder.comment("OptimizedFastEntityMovement — NeoForge 1.21.1")
               .push("general");

        optimizedFastEntityMovement = builder
                .comment(
                    "Enable the fast entity movement optimization.",
                    "When true, entities whose speed (length of movement vector) exceeds",
                    "`speedThreshold` blocks/tick will use a simplified single-axis collision",
                    "check instead of vanilla's full 3-axis sweep.",
                    "This is a server-side-only optimization; no client changes are needed.",
                    "Default: false  (vanilla behaviour unchanged)"
                )
                .define("optimizedFastEntityMovement", false);

        speedThreshold = builder
                .comment(
                    "Speed threshold in blocks/tick.",
                    "Only entities whose movement vector length is GREATER than this value",
                    "will use the optimized collision path.",
                    "0.5 ≈ 10 m/s (fast projectiles, TNT, ender pearls).",
                    "Set lower to optimize more entities; set higher to be more conservative.",
                    "Default: " + DEFAULT_SPEED_THRESHOLD
                )
                .defineInRange("speedThreshold", DEFAULT_SPEED_THRESHOLD, 0.0D, Double.MAX_VALUE);

        builder.pop();
    }

    // ─── Accesseurs ───────────────────────────────────────────────────────────

    /**
     * @return {@code true} si l'optimisation est active.
     */
    public boolean isEnabled() {
        return optimizedFastEntityMovement.get();
    }

    /**
     * @return seuil de vitesse en blocs/tick au-dessus duquel l'optimisation s'applique.
     */
    public double getSpeedThreshold() {
        return speedThreshold.get();
    }
}
