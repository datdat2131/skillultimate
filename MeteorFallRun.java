package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * METEOR FALL – thiên thạch rơi xuống ở cuối giai đoạn CHARGE (fall-ticks tick cuối), chạm đất = bắt đầu IMPACT.
 * params: fall-ticks, meteor-size, trail-length, slant (độ xiên của đường rơi),
 *         meteor: {core: {particle...}, glow: {...}, trail: {...}}
 */
public final class MeteorFallRun extends SkillRun {

    private final int fallTicks;
    private final double size;
    private final double trailLength;
    private final double slant;
    private final ParticleSpec core;
    private final ParticleSpec glow;
    private final ParticleSpec trail;
    private final Vector slantDir;

    public MeteorFallRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                         LivingEntity te, double power) {
        super(plugin, def, baseId, caster, target, te, power);
        ConfigurationSection p = def.params();
        fallTicks = Math.max(4, p.getInt("fall-ticks", 16));
        size = p.getDouble("meteor-size", 1.8);
        trailLength = p.getDouble("trail-length", 9.0);
        slant = p.getDouble("slant", 10.0);
        core = ParticleSpec.parse(p.getConfigurationSection("meteor.core"), plugin.getLogger());
        glow = ParticleSpec.parse(p.getConfigurationSection("meteor.glow"), plugin.getLogger());
        trail = ParticleSpec.parse(p.getConfigurationSection("meteor.trail"), plugin.getLogger());
        Vector d = target.toVector().subtract(caster.getLocation().toVector()).setY(0);
        if (d.lengthSquared() < 1.0E-4) d = caster.getLocation().getDirection().setY(0);
        if (d.lengthSquared() < 1.0E-4) d = new Vector(1, 0, 0);
        slantDir = d.normalize();
    }

    @Override
    protected void onPhaseTick(PhaseDef.Id id) {
        if (id != PhaseDef.Id.CHARGE) return;
        PhaseDef pd = def.phase(PhaseDef.Id.CHARGE);
        int start = pd.duration() - fallTicks;
        if (phaseTick < start) return;

        double f = (phaseTick - start + 1.0) / fallTicks;     // 0..1
        double e = f * f;                                     // rơi nhanh dần
        World w = target.getWorld();
        if (w == null) return;
        double sx = target.getX() + slantDir.getX() * slant;
        double sz = target.getZ() + slantDir.getZ() * slant;
        double sy = target.getY() + def.skyHeight();
        double x = sx + (target.getX() - sx) * e;
        double y = sy + (target.getY() - sy) * e;
        double z = sz + (target.getZ() - sz) * e;

        QualityManager q = plugin.quality();
        double dens = q.density();
        // thân thiên thạch: lõi + quầng sáng
        int nCore = Math.max(6, (int) Math.round(26 * dens));
        for (int i = 0; i < nCore; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / nCore);
            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5) + phaseTick * 0.4;
            double r = size * 0.6;
            dot(q, core, w, x + r * Math.sin(phi) * Math.cos(th), y + r * Math.cos(phi), z + r * Math.sin(phi) * Math.sin(th));
        }
        int nGlow = Math.max(4, (int) Math.round(14 * dens));
        for (int i = 0; i < nGlow; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / nGlow);
            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5) - phaseTick * 0.3;
            double r = size * 1.1;
            dot(q, glow, w, x + r * Math.sin(phi) * Math.cos(th), y + r * Math.cos(phi), z + r * Math.sin(phi) * Math.sin(th));
        }
        // đuôi lửa/khói phía sau (hướng về điểm xuất phát)
        double dx = sx - x, dy = sy - y, dz = sz - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-4) {
            int nTrail = Math.max(4, (int) Math.round(trailLength * 1.5 * dens));
            for (int i = 1; i <= nTrail; i++) {
                double k = trailLength * i / nTrail;
                double jitter = 0.25 * (1 + k * 0.08);
                dot(q, trail, w, x + dx / len * k + (Math.random() - 0.5) * jitter,
                        y + dy / len * k + (Math.random() - 0.5) * jitter,
                        z + dz / len * k + (Math.random() - 0.5) * jitter);
            }
        }
    }

    private void dot(QualityManager q, ParticleSpec sp, World w, double x, double y, double z) {
        if (sp == null || !q.tryUse(1)) return;
        sp.spawn(w, x, y, z, 1, 0, 0, 0, 0.02);
    }
}
