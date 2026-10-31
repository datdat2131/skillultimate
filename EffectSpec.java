package me.astrax.ultimate;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Hiệu ứng potion cấu hình bằng YAML: {type: SLOWNESS, duration: 60, amplifier: 1, chance: 1.0}. type FIRE = đốt cháy. */
public record EffectSpec(String type, int duration, int amplifier, double chance) {

    public static List<EffectSpec> parse(List<?> raw) {
        List<EffectSpec> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object t = m.get("type");
            if (t == null) continue;
            out.add(new EffectSpec(String.valueOf(t).toUpperCase(Locale.ROOT),
                    (int) num(m.get("duration"), 100), (int) num(m.get("amplifier"), 0), num(m.get("chance"), 1.0)));
        }
        return out;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                // dùng mặc định
            }
        }
        return def;
    }

    public static PotionEffectType potion(String name) {
        return Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }

    public void apply(LivingEntity target) {
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) return;
        if (type.equals("FIRE")) {
            target.setFireTicks(Math.max(target.getFireTicks(), duration));
            return;
        }
        PotionEffectType pt = potion(type);
        if (pt == null) return;
        target.addPotionEffect(new PotionEffect(pt, duration, amplifier, false, true, true));
    }
}
