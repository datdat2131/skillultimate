package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * VOID BREAKER – quả cầu hư không hình thành ở giai đoạn CHARGE (vẽ thêm lõi tối + vành xoáy),
 * co lại ở đầu IMPACT rồi phát nổ. Void Mark/Darkness/Slow do pulse và combat trong YAML quyết định.
 * params: orb: {radius, height, core: {particle...}, ring: {particle...}}
 */
public final class VoidBreakerRun extends SkillRun {

    private final double orbRadius;
    private final double orbHeight;
    private final ParticleSpec core;
    private final ParticleSpec ring;

    public VoidBreakerRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                          LivingEntity te, double power) {
        super(plugin, def, baseId, caster, target, te, power);
        ConfigurationSection p = def.params();
        orbRadius = p.getDouble("orb.radius", 1.6);
        orbHeight = p.getDouble("orb.height", 2.6);
        core = ParticleSpec.parse(p.getConfigurationSection("orb.core"), plugin.getLogger());
        ring = ParticleSpec.parse(p.getConfigurationSection("orb.ring"), plugin.getLogger());
    }

    @Override
    protected void onPhaseTick(PhaseDef.Id id) {
        double scale;
        if (id == PhaseDef.Id.CHARGE) {
            PhaseDef pd = def.phase(PhaseDef.Id.CHARGE);
            double prog = Math.min(1.0, (phaseTick + 1.0) / pd.duration());
            scale = (0.25 + 0.75 * prog) * (1.0 + 0.08 * Math.sin(phaseTick * 0.5));
        } else if (id == PhaseDef.Id.IMPACT) {
            PhaseDef pd = def.phase(PhaseDef.Id.IMPACT);
            int collapse = Math.max(1, pd.combatDelay());
            if (phaseTick >= collapse) return;
            scale = 1.0 - (double) phaseTick / collapse;       // co lại trước khi nổ
        } else {
            return;
        }
        drawOrb(scale);
    }

    private void drawOrb(double scale) {
        World w = target.getWorld();
        if (w == null) return;
        QualityManager q = plugin.quality();
        double dens = q.density();
        double cx = target.getX(), cy = target.getY() + orbHeight, cz = target.getZ();
        double r = orbRadius * scale;
        if (r < 0.05) return;
        int n = Math.max(8, (int) Math.round(34 * dens));
        for (int i = 0; i < n; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / n);
            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5) + phaseTick * 0.25;
            if (core != null && q.tryUse(1)) {
                core.spawn(w, cx + r * Math.sin(phi) * Math.cos(th), cy + r * Math.cos(phi),
                        cz + r * Math.sin(phi) * Math.sin(th), 1, 0, 0, 0, 0);
            }
        }
        // vành xoáy nghiêng quanh quả cầu
        int m = Math.max(10, (int) Math.round(36 * dens));
        double rr = r * 1.9;
        for (int i = 0; i < m; i++) {
            double a = 2 * Math.PI * i / m + phaseTick * 0.45;
            double x = rr * Math.cos(a), z = rr * Math.sin(a);
            double tilt = 0.5;
            if (ring != null && q.tryUse(1)) {
                ring.spawn(w, cx + x, cy - z * Math.sin(tilt), cz + z * Math.cos(tilt), 1, 0, 0, 0, 0);
            }
        }
    }
}
