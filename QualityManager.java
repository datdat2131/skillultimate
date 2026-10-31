package me.astrax.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Chất lượng hiệu ứng + ngân sách particle mỗi tick + tự giảm khi server đông người.
 * Mọi layer particle đều đi qua {@link #tryUse(int)} nên không thể spam vượt ngân sách.
 */
public final class QualityManager {

    public enum Quality {
        LOW, MEDIUM, HIGH, ULTRA;

        public static Quality parse(String s, Quality def) {
            if (s == null) return def;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    private final AstraXUltimate plugin;
    private Quality quality = Quality.HIGH;
    private double density = 1.0;
    private int budgetMax = 1200;
    private int layerInterval = 1;
    private boolean shake = true;

    private int budget = 1200;
    private double autoFactor = 1.0;
    private int counter;

    public QualityManager(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigurationSection c = plugin.getConfig();
        quality = Quality.parse(c.getString("effects-quality", "HIGH"), Quality.HIGH);
        String p = "quality." + quality.name() + ".";
        density = c.getDouble(p + "density", 1.0);
        budgetMax = Math.max(50, c.getInt(p + "budget", 1200));
        layerInterval = Math.max(1, c.getInt(p + "layer-interval", 1));
        shake = c.getBoolean(p + "camera-shake", true);
        budget = budgetMax;
        updateAuto();
    }

    /** Gọi mỗi tick: nạp lại ngân sách particle. */
    public void tick() {
        if (++counter % 40 == 0) updateAuto();
        budget = (int) (budgetMax * autoFactor);
    }

    private void updateAuto() {
        ConfigurationSection c = plugin.getConfig();
        if (!c.getBoolean("auto-scale.enabled", true)) {
            autoFactor = 1.0;
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        int threshold = Math.max(1, c.getInt("auto-scale.players-threshold", 40));
        double min = Math.max(0.1, c.getDouble("auto-scale.min-factor", 0.4));
        autoFactor = online <= threshold ? 1.0 : Math.max(min, (double) threshold / online);
    }

    /** Xin dùng {@code calls} lệnh spawn particle trong tick này. */
    public boolean tryUse(int calls) {
        if (budget < calls) return false;
        budget -= calls;
        return true;
    }

    public void setQuality(Quality q) {
        plugin.getConfig().set("effects-quality", q.name());
        plugin.saveConfig();
        load();
    }

    public Quality quality() { return quality; }
    public double density() { return density * autoFactor; }
    public int layerInterval() { return layerInterval; }
    public boolean shake() { return shake; }
}
