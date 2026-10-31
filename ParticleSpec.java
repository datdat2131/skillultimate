package me.astrax.ultimate;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Một loại particle cấu hình bằng YAML, hỗ trợ dữ liệu đi kèm:
 * <pre>
 * particle: DUST                 # tên Particle của Bukkit
 * color: '#FF6A00'               # DUST / DUST_COLOR_TRANSITION / ENTITY_EFFECT   ("r,g,b" cũng được)
 * to-color: '#220000'            # DUST_COLOR_TRANSITION
 * size: 1.5                      # DUST / DUST_COLOR_TRANSITION
 * block: MAGMA_BLOCK             # BLOCK / FALLING_DUST / BLOCK_MARKER ...
 * item: IRON_SWORD               # ITEM
 * </pre>
 */
public final class ParticleSpec {

    private final Particle particle;
    private final Object data;
    private final boolean directional;

    private ParticleSpec(Particle particle, Object data, boolean directional) {
        this.particle = particle;
        this.data = data;
        this.directional = directional;
    }

    public static ParticleSpec parse(ConfigurationSection s, Logger log) {
        if (s == null) return null;
        String name = s.getString("particle");
        if (name == null || name.equalsIgnoreCase("NONE")) return null;
        Particle p;
        try {
            p = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Particle không hợp lệ: " + name);
            return null;
        }
        Class<?> dt = p.getDataType();
        float size = (float) s.getDouble("size", 1.0);
        Object data = null;
        boolean dir = false;
        if (dt == Void.class) {
            dir = true;
        } else if (dt == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color(s.getString("color", "#FFFFFF")), size);
        } else if (dt == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color(s.getString("color", "#FFFFFF")),
                    color(s.getString("to-color", s.getString("color", "#000000"))), size);
        } else if (dt == BlockData.class) {
            Material m = Material.matchMaterial(s.getString("block", "STONE"));
            if (m == null || !m.isBlock()) m = Material.STONE;
            data = m.createBlockData();
            dir = true;
        } else if (dt == ItemStack.class) {
            Material m = Material.matchMaterial(s.getString("item", "STONE"));
            if (m == null || !m.isItem()) m = Material.STONE;
            data = new ItemStack(m);
            dir = true;
        } else if (dt == Color.class) {
            data = color(s.getString("color", "#FFFFFF"));
        } else {
            log.warning("Particle " + p + " cần dữ liệu đặc biệt, không được hỗ trợ.");
            return null;
        }
        return new ParticleSpec(p, data, dir);
    }

    public static Color color(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(t.substring(1), 16));
            }
            String[] p = t.split(",");
            return Color.fromRGB(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    /** true nếu particle nhận vận tốc khi count = 0 (offset là hướng, extra là tốc độ). */
    public boolean directional() {
        return directional;
    }

    public void spawn(World w, double x, double y, double z, int count, double ox, double oy, double oz, double extra) {
        w.spawnParticle(particle, x, y, z, count, ox, oy, oz, extra, data);
    }
}
