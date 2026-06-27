package fr.ladycraftfr.ofem;

import fr.ladycraftfr.ofem.config.OFEMConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Point d'entrée du mod <em>OptimizedFastEntityMovement</em> pour NeoForge 1.21.1.
 *
 * <p>Ce mod porte la règle {@code optimizedFastEntityMovement} de
 * <a href="https://github.com/TISUnion/Carpet-TIS-Addition">Carpet TIS Addition</a>
 * (auteur : fallen-breath, licence LGPL-3.0) vers NeoForge en utilisant
 * SpongePowered Mixins et les mappings Mojang.</p>
 *
 * <h2>Comportement</h2>
 * <p>Quand l'option est activée dans {@code ofem-server.toml}, toute entité dont
 * la longueur du vecteur de déplacement dépasse {@code speedThreshold} blocs/tick
 * utilisera une détection de collision simplifiée sur l'axe dominant au lieu du
 * balayage 3D complet vanilla. En dessous du seuil, le comportement vanilla est
 * intégralement préservé.</p>
 *
 * <h2>Sécurité</h2>
 * <ul>
 *   <li>SERVER-SIDE ONLY — aucun code client n'est inclus.</li>
 *   <li>En cas de doute (vitesse négative, NaN, entité non standard), le code
 *       revient automatiquement au comportement vanilla.</li>
 * </ul>
 */
@Mod(OFEMMod.MOD_ID)
public class OFEMMod {

    public static final String MOD_ID = "ofem";
    public static final Logger LOGGER  = LogManager.getLogger(MOD_ID);

    public OFEMMod(IEventBus modEventBus, ModContainer modContainer) {
        // Enregistrement de la configuration server-side
        modContainer.registerConfig(ModConfig.Type.SERVER, OFEMConfig.SPEC, "ofem-server.toml");

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[OFEM] OptimizedFastEntityMovement chargé.");
        LOGGER.info("[OFEM] Port de Carpet TIS Addition (fallen-breath) — LGPL-3.0");
        LOGGER.info("[OFEM] Configurez le mod dans config/ofem-server.toml");
    }
}
