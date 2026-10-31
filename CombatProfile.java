package me.astrax.ultimate;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Hồ sơ chiến đấu của một đòn đánh/pulse, đọc từ mục combat của YAML:
 * <pre>
 * combat:
 *   damage: 45                 # sát thương gốc (nhân với power skill và combat.damage-multiplier)
 *   falloff: 0.3               # giảm sát thương ở rìa bán kính (0 = không giảm)
 *   crit: {chance: 0.2, multiplier: 1.6}
 *   true-damage: 0.1           # tỉ lệ sát thương bỏ qua giáp
 *   armor-penetration: 0.2     # tỉ lệ giáp bị bỏ qua cho phần sát thương thường
 *   knockback: {strength: 2.0, lift: 0.8, mode: RADIAL}     # RADIAL | PULL | DIRECTIONAL
 *   burn: 160   freeze: 80   stun: 20   silence: 60   darkness: 80      (tick)
 *   slow: {duration: 60, amplifier: 2}
 *   mark: {id: VOID, duration: 200}              # đánh dấu mục tiêu
 *   consume-mark: {id: VOID, bonus: 0.6}         # mục tiêu có mark: +60% sát thương và gỡ mark
 *   execute: {threshold: 0.15, multiplier: 2.0}  # máu ≤ 15% thì nhân sát thương
 *   shield-break: 100                            # khóa khiên (tick)
 *   effects: [{type: WITHER, duration: 80, amplifier: 1}]
 *   max-targets: 30   hit-players: true   hit-mobs: true   hit-passive: false   indicators: true
 * </pre>
 */
public final class CombatProfile {

    public final double damage;
    public final double falloff;
    public final double critChance;
    public final double critMultiplier;
    public final double trueFraction;
    public final double armorPen;
    public final double knockback;
    public final double lift;
    public final String kbMode;
    public final int burn;
    public final int freeze;
    public final int stun;
    public final int silence;
    public final int darkness;
    public final int slowTicks;
    public final int slowAmp;
    public final String markId;
    public final int markTicks;
    public final String consumeId;
    public final double consumeBonus;
    public final double executeThreshold;
    public final double executeMultiplier;
    public final int shieldBreak;
    public final int maxTargets;
    public final boolean hitPlayers;
    public final boolean hitMobs;
    public final boolean hitPassive;
    public final boolean indicators;
    public final List<EffectSpec> effects;

    private CombatProfile(ConfigurationSection s) {
        damage = s.getDouble("damage", 0.0);
        falloff = Math.max(0.0, Math.min(1.0, s.getDouble("falloff", 0.0)));
        critChance = s.getDouble("crit.chance", 0.0);
        critMultiplier = s.getDouble("crit.multiplier", 1.5);
        trueFraction = Math.max(0.0, Math.min(1.0, s.getDouble("true-damage", 0.0)));
        armorPen = Math.max(0.0, Math.min(1.0, s.getDouble("armor-penetration", 0.0)));
        knockback = s.getDouble("knockback.strength", 0.0);
        lift = s.getDouble("knockback.lift", 0.0);
        kbMode = s.getString("knockback.mode", "RADIAL").toUpperCase(Locale.ROOT);
        burn = s.getInt("burn", 0);
        freeze = s.getInt("freeze", 0);
        stun = s.getInt("stun", 0);
        silence = s.getInt("silence", 0);
        darkness = s.getInt("darkness", 0);
        slowTicks = s.getInt("slow.duration", 0);
        slowAmp = s.getInt("slow.amplifier", 1);
        markId = s.getString("mark.id") == null ? null : s.getString("mark.id").toUpperCase(Locale.ROOT);
        markTicks = s.getInt("mark.duration", 200);
        consumeId = s.getString("consume-mark.id") == null ? null : s.getString("consume-mark.id").toUpperCase(Locale.ROOT);
        consumeBonus = s.getDouble("consume-mark.bonus", 0.5);
        executeThreshold = s.getDouble("execute.threshold", 0.0);
        executeMultiplier = s.getDouble("execute.multiplier", 1.0);
        shieldBreak = s.getInt("shield-break", 0);
        maxTargets = Math.max(1, s.getInt("max-targets", 30));
        hitPlayers = s.getBoolean("hit-players", true);
        hitMobs = s.getBoolean("hit-mobs", true);
        hitPassive = s.getBoolean("hit-passive", false);
        indicators = s.getBoolean("indicators", true);
        effects = EffectSpec.parse(s.getList("effects"));
    }

    /** Trả về null nếu section null. */
    public static CombatProfile parse(ConfigurationSection s) {
        return s == null ? null : new CombatProfile(s);
    }
}
