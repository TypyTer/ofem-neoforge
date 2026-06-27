package fr.ladycraftfr.ofem.config;

public class OFEMConfig {

    public static final OFEMConfig INSTANCE = new OFEMConfig();

    private boolean enabled = true;

    // seuil vitesse (ajuste selon tests)
    private double speedThreshold = 0.6;

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeedThreshold() {
        return speedThreshold;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSpeedThreshold(double speedThreshold) {
        this.speedThreshold = speedThreshold;
    }
}
