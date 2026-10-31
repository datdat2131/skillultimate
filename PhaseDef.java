package me.astrax.ultimate;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Một giai đoạn của skill (CAST / CHARGE / IMPACT / AFTER), đọc từ YAML:
 * <pre>
 * charge:
 *   duration: 44               # tick
 *   lock-movement: true        # khóa di chuyển người dùng trong giai đoạn này
 *   actionbar: '&6&l☄ METEOR FALL'
 *   sounds: {rise: {key: block.beacon.ambient, volume: 1.5, pitch: 0.5, pitch-end: 2.0, interval: 4}}
 *   layers: {circle: {type: CIRCLE, ...}}
 *   pulse:  {interval: 10, until: 0, radius: 10, at: TARGET, combat: {...}, caster-effects: [...]}
 *   shake:  {radius: 30, duration: 16, intensity: 2.5}
 *   radius: 12                 # (IMPACT) bán kính đòn đánh chính
 *   combat: {...}              # (IMPACT) đòn đánh chính
 *   combat-delay: 0            # (IMPACT) trễ bao nhiêu tick kể từ đầu giai đoạn mới gây sát thương
 * </pre>
 */
public final class PhaseDef {

    public enum Id { CAST, CHARGE, IMPACT, AFTER }

    public record Pulse(int interval, int until, double radius, boolean atCaster, CombatProfile combat,
                        List<EffectSpec> casterEffects) {}

    public record Shake(double radius, int duration, double intensity) {}

    private final Id id;
    private final int duration;
    private final boolean lockMovement;
    private final String actionbar;
    private final List<SoundSpec> sounds = new ArrayList<>();
    private final List<VisualLayer> layers = new ArrayList<>();
    private final Pulse pulse;
    private final Shake shake;
    private final double radius;
    private final CombatProfile combat;
    private final int combatDelay;

    public PhaseDef(Id id, ConfigurationSection s, Logger log) {
        this.id = id;
        this.duration = Math.max(1, s.getInt("duration", 20));
        this.lockMovement = s.getBoolean("lock-movement", false);
        this.actionbar = s.getString("actionbar", "");
        this.radius = s.getDouble("radius", 6.0);
        this.combat = CombatProfile.parse(s.getConfigurationSection("combat"));
        this.combatDelay = Math.max(0, s.getInt("combat-delay", 0));

        ConfigurationSection ss = s.getConfigurationSection("sounds");
        if (ss != null) {
            for (String k : ss.getKeys(false)) {
                SoundSpec sp = SoundSpec.parse(ss.getConfigurationSection(k));
                if (sp != null) sounds.add(sp);
            }
        }
        ConfigurationSection ls = s.getConfigurationSection("layers");
        if (ls != null) {
            for (String k : ls.getKeys(false)) {
                VisualLayer l = VisualLayer.parse(k, ls.getConfigurationSection(k), log);
                if (l != null) layers.add(l);
            }
        }

        ConfigurationSection pu = s.getConfigurationSection("pulse");
        if (pu != null) {
            CombatProfile pc = CombatProfile.parse(pu.getConfigurationSection("combat"));
            List<EffectSpec> ce = EffectSpec.parse(pu.getList("caster-effects"));
            boolean atCaster = "CASTER".equals(pu.getString("at", "TARGET").toUpperCase(Locale.ROOT));
            this.pulse = new Pulse(Math.max(1, pu.getInt("interval", 10)), pu.getInt("until", 0),
                    pu.getDouble("radius", 8.0), atCaster, pc, ce);
        } else {
            this.pulse = null;
        }

        ConfigurationSection sh = s.getConfigurationSection("shake");
        this.shake = sh == null ? null
                : new Shake(sh.getDouble("radius", 25.0), Math.max(2, sh.getInt("duration", 12)),
                sh.getDouble("intensity", 2.0));
    }

    public Id id() { return id; }
    public int duration() { return duration; }
    public boolean lockMovement() { return lockMovement; }
    public String actionbar() { return actionbar; }
    public List<SoundSpec> sounds() { return Collections.unmodifiableList(sounds); }
    public List<VisualLayer> layers() { return Collections.unmodifiableList(layers); }
    public Pulse pulse() { return pulse; }
    public Shake shake() { return shake; }
    public double radius() { return radius; }
    public CombatProfile combat() { return combat; }
    public int combatDelay() { return combatDelay; }
}
