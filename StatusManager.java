package me.astrax.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trạng thái chiến đấu tạm thời: Stun, Silence, Freeze, Mark. Mọi trạng thái có hạn (tick của plugin),
 * tự hết hạn, tự dọn khi thực thể chết/thoát, và khôi phục AI của mob khi tắt plugin.
 */
public final class StatusManager {

    private final AstraXUltimate plugin;
    private final Map<UUID, Long> stunned = new HashMap<>();
    private final Map<UUID, Long> silenced = new HashMap<>();
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Map<UUID, Map<String, Long>> marks = new HashMap<>();
    private final Set<UUID> aiOff = new HashSet<>();
    private final Map<String, ParticleSpec> markSpecs = new HashMap<>();
    private int counter;

    public StatusManager(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    /** Nạp hiển thị của các loại Mark từ config.yml (mục marks). */
    public void load() {
        markSpecs.clear();
        ConfigurationSection ms = plugin.getConfig().getConfigurationSection("marks");
        if (ms == null) return;
        for (String k : ms.getKeys(false)) {
            ParticleSpec sp = ParticleSpec.parse(ms.getConfigurationSection(k), plugin.getLogger());
            if (sp != null) markSpecs.put(k.toUpperCase(), sp);
        }
    }

    // ---------------- Áp trạng thái ----------------
    public void stun(LivingEntity e, int ticks) {
        if (ticks <= 0) return;
        stunned.merge(e.getUniqueId(), plugin.now() + ticks, Math::max);
        if (e instanceof Player p) {
            plugin.skills().interrupt(p.getUniqueId(), "choáng");
            applyPotion(p, "slowness", ticks, 6);
        } else {
            e.setAI(false);
            aiOff.add(e.getUniqueId());
        }
        e.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, e.getHeight() + 0.3, 0), 8, 0.3, 0.1, 0.3, 0.1);
    }

    public void silence(LivingEntity e, int ticks) {
        if (ticks <= 0) return;
        silenced.merge(e.getUniqueId(), plugin.now() + ticks, Math::max);
        if (e instanceof Player p) plugin.skills().interrupt(p.getUniqueId(), "câm lặng");
    }

    public void freeze(LivingEntity e, int ticks) {
        if (ticks <= 0) return;
        frozen.merge(e.getUniqueId(), plugin.now() + ticks, Math::max);
        applyPotion(e, "slowness", ticks, 3);
        e.setFreezeTicks(e.getMaxFreezeTicks());
    }

    public void mark(LivingEntity e, String id, int ticks) {
        if (id == null || ticks <= 0) return;
        marks.computeIfAbsent(e.getUniqueId(), k -> new HashMap<>()).merge(id, plugin.now() + ticks, Math::max);
    }

    private void applyPotion(LivingEntity e, String name, int ticks, int amp) {
        PotionEffectType t = EffectSpec.potion(name);
        if (t != null) e.addPotionEffect(new PotionEffect(t, ticks, amp, false, false, false));
    }

    // ---------------- Truy vấn ----------------
    public boolean isStunned(UUID id) {
        Long u = stunned.get(id);
        return u != null && u > plugin.now();
    }

    public boolean isSilenced(UUID id) {
        Long u = silenced.get(id);
        return u != null && u > plugin.now();
    }

    public boolean hasMark(UUID id, String mark) {
        Map<String, Long> m = marks.get(id);
        if (m == null) return false;
        Long u = m.get(mark);
        return u != null && u > plugin.now();
    }

    public void removeMark(UUID id, String mark) {
        Map<String, Long> m = marks.get(id);
        if (m == null) return;
        m.remove(mark);
        if (m.isEmpty()) marks.remove(id);
    }

    // ---------------- Dọn dẹp ----------------
    /** Xóa mọi trạng thái của một thực thể (chết/thoát). */
    public void clear(UUID id) {
        stunned.remove(id);
        silenced.remove(id);
        frozen.remove(id);
        marks.remove(id);
        if (aiOff.remove(id)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) le.setAI(true);
        }
    }

    /** Gọi mỗi 2 tick. */
    public void tick() {
        long now = plugin.now();
        counter++;
        expire(stunned, now, true);
        expire(silenced, now, false);

        for (Iterator<Map.Entry<UUID, Long>> it = frozen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Entity ent = Bukkit.getEntity(e.getKey());
            if (e.getValue() <= now || !(ent instanceof LivingEntity le) || !le.isValid()) {
                it.remove();
                continue;
            }
            le.setFreezeTicks(le.getMaxFreezeTicks());
            if (counter % 3 == 0) {
                le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0.01);
            }
        }

        boolean visual = counter % 5 == 0;
        int drawn = 0;
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = marks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Long>> e = it.next();
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isValid()) {
                it.remove();
                continue;
            }
            Map<String, Long> m = e.getValue();
            m.values().removeIf(u -> u <= now);
            if (m.isEmpty()) {
                it.remove();
                continue;
            }
            if (visual && drawn < 30) {
                for (String id : m.keySet()) {
                    ParticleSpec sp = markSpecs.get(id);
                    Location l = le.getLocation().add(0, le.getHeight() + 0.5, 0);
                    if (sp != null && plugin.quality().tryUse(1)) {
                        sp.spawn(le.getWorld(), l.getX(), l.getY(), l.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
                        drawn++;
                    }
                }
            }
        }
    }

    private void expire(Map<UUID, Long> map, long now, boolean restoreAi) {
        for (Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            if (restoreAi && aiOff.remove(e.getKey())) {
                Entity ent = Bukkit.getEntity(e.getKey());
                if (ent instanceof LivingEntity le) le.setAI(true);
            }
        }
    }

    /** Khôi phục AI của mọi mob đang bị choáng khi tắt plugin. */
    public void shutdown() {
        for (UUID id : new HashSet<>(aiOff)) {
            Entity ent = Bukkit.getEntity(id);
            if (ent instanceof LivingEntity le) le.setAI(true);
        }
        aiOff.clear();
        stunned.clear();
        silenced.clear();
        frozen.clear();
        marks.clear();
    }
}
