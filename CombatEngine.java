package me.astrax.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hệ chiến đấu của skill: sát thương nhân hệ số, chí mạng, true damage, xuyên giáp, knockback,
 * choáng/chậm/đốt/đóng băng/câm lặng, đánh dấu (mark), execute, phá khiên, số sát thương nổi.
 * Không bao giờ phá block.
 */
public final class CombatEngine {

    /** Kết quả của một đòn đánh diện rộng. */
    public static final class Result {
        public int hits;
        public int crits;
        public double totalDamage;
        public int indicators;
        public final List<LivingEntity> targets = new ArrayList<>();
    }

    private final AstraXUltimate plugin;
    private final Set<TextDisplay> indicators = new HashSet<>();

    public CombatEngine(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    // ---------------- Chọn mục tiêu ----------------
    public boolean valid(Player caster, LivingEntity le, CombatProfile p) {
        if (le.equals(caster) || le instanceof ArmorStand || le.isDead() || !le.isValid()) return false;
        if (le instanceof Player tp) {
            if (!p.hitPlayers || !le.getWorld().getPVP()) return false;
            GameMode gm = tp.getGameMode();
            return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE;
        }
        if (!p.hitMobs) return false;
        if (le instanceof Enemy || le instanceof Boss) return true;
        if (le instanceof Tameable t && t.isTamed()) return false;
        return p.hitPassive;
    }

    /** Tìm mục tiêu hợp lệ trong bán kính, gần nhất trước. */
    public List<LivingEntity> findTargets(Player caster, Location center, double radius, CombatProfile p) {
        List<LivingEntity> list = new ArrayList<>();
        World w = center.getWorld();
        if (w == null) return list;
        double r2 = radius * radius;
        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || !valid(caster, le, p)) continue;
            if (le.getLocation().add(0, le.getHeight() / 2, 0).distanceSquared(center) > r2) continue;
            list.add(le);
        }
        list.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
        return list;
    }

    // ---------------- Đòn đánh diện rộng ----------------
    public Result strike(Player caster, Location center, double radius, CombatProfile p, double power, Vector dir) {
        return strike(caster, center, radius, p, power, dir, null);
    }

    /** exclude: các UUID đã bị trúng (không trúng lần nữa, dùng cho đòn quét dọc đường đi). */
    public Result strike(Player caster, Location center, double radius, CombatProfile p, double power,
                         Vector dir, Set<UUID> exclude) {
        Result res = new Result();
        if (p == null || center.getWorld() == null) return res;
        int max = Math.min(p.maxTargets, plugin.getConfig().getInt("limits.max-targets-per-hit", 30));
        int n = 0;
        for (LivingEntity le : findTargets(caster, center, radius, p)) {
            if (exclude != null && exclude.contains(le.getUniqueId())) continue;
            if (n++ >= max) break;
            double dist = le.getLocation().distance(center);
            double factor = radius <= 0 ? 1.0 : 1.0 - p.falloff * Math.min(1.0, dist / radius);
            hit(caster, le, center, p, power, factor, dir, res);
            if (exclude != null) exclude.add(le.getUniqueId());
        }
        return res;
    }

    // ---------------- Trúng một mục tiêu ----------------
    public void hit(Player caster, LivingEntity t, Location origin, CombatProfile p, double power, double factor,
                    Vector dir, Result res) {
        double dealt = 0.0;
        boolean crit = false;
        if (p.damage > 0) {
            double dmg = p.damage * power * factor * plugin.getConfig().getDouble("combat.damage-multiplier", 1.0);

            if (p.critChance > 0 && ThreadLocalRandom.current().nextDouble() < p.critChance) {
                dmg *= p.critMultiplier;
                crit = true;
            }
            if (p.executeThreshold > 0 && attr(t, "max_health", 20.0) > 0
                    && t.getHealth() / attr(t, "max_health", 20.0) <= p.executeThreshold) {
                dmg *= p.executeMultiplier;
            }
            if (p.consumeId != null && plugin.statuses().hasMark(t.getUniqueId(), p.consumeId)) {
                dmg *= 1.0 + p.consumeBonus;
                plugin.statuses().removeMark(t.getUniqueId(), p.consumeId);
                t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 16, 0.3, 0.5, 0.3, 0.3);
            }

            double trueDmg = dmg * p.trueFraction;
            double normal = dmg - trueDmg;
            if (plugin.getConfig().getBoolean("combat.ignore-invulnerability-frames", true)) t.setNoDamageTicks(0);
            if (normal > 0.01) t.damage(armorAdjust(t, normal, p.armorPen), caster);
            if (trueDmg > 0.01 && !t.isDead()) trueDamage(t, caster, trueDmg);
            dealt = dmg;
            res.totalDamage += dealt;
            if (crit) res.crits++;
        }
        res.hits++;
        res.targets.add(t);
        if (t.isDead() || !t.isValid()) return;

        applyKnockback(t, origin, p, dir);
        applyStatuses(t, p);
        if (dealt > 0 && p.indicators && plugin.getConfig().getBoolean("combat.damage-indicators", true)
                && res.indicators < plugin.getConfig().getInt("limits.max-damage-indicators", 12)) {
            res.indicators++;
            indicator(t, dealt, crit);
        }
    }

    /** Chỉ áp trạng thái (dùng cho pulse không gây sát thương). */
    public void applyStatuses(LivingEntity t, CombatProfile p) {
        if (p.burn > 0) t.setFireTicks(Math.max(t.getFireTicks(), p.burn));
        if (p.freeze > 0) plugin.statuses().freeze(t, p.freeze);
        if (p.stun > 0) plugin.statuses().stun(t, p.stun);
        if (p.silence > 0) plugin.statuses().silence(t, p.silence);
        if (p.slowTicks > 0) potion(t, "slowness", p.slowTicks, p.slowAmp);
        if (p.darkness > 0) potion(t, "darkness", p.darkness, 0);
        if (p.markId != null) plugin.statuses().mark(t, p.markId, p.markTicks);
        for (EffectSpec e : p.effects) e.apply(t);
        if (p.shieldBreak > 0 && t instanceof Player pl && pl.isBlocking()) {
            pl.setCooldown(Material.SHIELD, p.shieldBreak);
            pl.clearActiveItem();
        }
    }

    private void potion(LivingEntity t, String name, int ticks, int amp) {
        PotionEffectType type = EffectSpec.potion(name);
        if (type != null) t.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
    }

    private void applyKnockback(LivingEntity t, Location origin, CombatProfile p, Vector dir) {
        if (p.knockback <= 0 && p.lift <= 0) return;
        Vector v;
        switch (p.kbMode) {
            case "PULL" -> v = origin.toVector().subtract(t.getLocation().toVector());
            case "DIRECTIONAL" -> v = dir == null ? new Vector(0, 0, 0) : dir.clone();
            default -> v = t.getLocation().toVector().subtract(origin.toVector());
        }
        v.setY(0);
        if (v.lengthSquared() > 1.0E-6) v.normalize();
        double resist = Math.min(1.0, attr(t, "knockback_resistance", 0.0));
        v.multiply(p.knockback * (1.0 - resist)).setY(p.lift * (1.0 - resist * 0.5));
        t.setVelocity(v);
    }

    // ---------------- Công thức sát thương ----------------
    /** Bù trừ để phần sát thương thường bỏ qua {@code pen} (0..1) giáp của mục tiêu. */
    private double armorAdjust(LivingEntity t, double dmg, double pen) {
        if (pen <= 0) return dmg;
        double armor = attr(t, "armor", 0.0);
        if (armor <= 0) return dmg;
        double tough = attr(t, "armor_toughness", 0.0);
        double red = reduction(armor, tough, dmg);
        double red2 = reduction(armor * (1.0 - pen), tough, dmg);
        double denom = 1.0 - red;
        return denom <= 0.01 ? dmg : dmg * (1.0 - red2) / denom;
    }

    private double reduction(double armor, double tough, double dmg) {
        return Math.min(20.0, Math.max(armor / 5.0, armor - 4.0 * dmg / (tough + 8.0))) / 25.0;
    }

    /** True damage: trừ thẳng máu, bỏ qua giáp. Nếu gây chết thì giết qua pipeline sát thương (có kill credit). */
    private void trueDamage(LivingEntity t, Player caster, double amount) {
        double h = t.getHealth();
        if (amount >= h) {
            t.damage(Math.max(1000.0, h * 100.0), caster);
            return;
        }
        t.setHealth(h - amount);
        t.playHurtAnimation(0f);
    }

    static double attr(LivingEntity e, String name, double def) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
        AttributeInstance inst = a == null ? null : e.getAttribute(a);
        return inst == null ? def : inst.getValue();
    }

    // ---------------- Số sát thương nổi ----------------
    private void indicator(LivingEntity t, double dmg, boolean crit) {
        Location loc = t.getLocation().add(ThreadLocalRandom.current().nextDouble(-0.4, 0.4),
                t.getHeight() + 0.2, ThreadLocalRandom.current().nextDouble(-0.4, 0.4));
        Component text = Component.text((crit ? "✦ " : "-") + Math.round(dmg), crit ? NamedTextColor.GOLD : NamedTextColor.RED)
                .decorate(TextDecoration.BOLD);
        TextDisplay d = loc.getWorld().spawn(loc, TextDisplay.class, disp -> {
            disp.text(text);
            disp.setBillboard(Display.Billboard.CENTER);
            disp.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            disp.setPersistent(false);
            disp.setInvulnerable(true);
            disp.setGravity(false);
            disp.setViewRange(0.4f);
            disp.setTeleportDuration(0);
            float sc = crit ? 1.6f : 1.2f;
            disp.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
        indicators.add(d);
        d.setTeleportDuration(14);
        d.teleport(loc.clone().add(0, 1.0, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            d.remove();
            indicators.remove(d);
        }, 18L);
    }

    /** Dọn mọi số sát thương nổi còn sót khi tắt plugin. */
    public void shutdown() {
        for (TextDisplay d : new ArrayList<>(indicators)) d.remove();
        indicators.clear();
    }
}
