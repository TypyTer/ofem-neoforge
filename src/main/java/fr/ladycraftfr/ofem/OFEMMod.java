package fr.ladycraftfr.ofem;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(OFEMMod.MODID)
public class OFEMMod {

    public static final String MODID = "ofem";
    public static final Logger LOGGER = LoggerFactory.getLogger("OFEM");

    public OFEMMod() {
        LOGGER.info("[OFEM] Loaded - Fast Entity Movement optimization active");
    }
}
