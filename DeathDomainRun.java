package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * DEATH DOMAIN – vùng lãnh địa quanh chủ nhân (CHARGE: vùng đang hoạt động, pulse làm chậm/tối màn hình kẻ địch
 * và buff chủ nhân), IMPACT: sụp đổ hút vào tâm rồi phát nổ.
 * params: radius (bán kính vùng, dùng để cảnh báo người trong vùng), warning (ActionBar cho người trong vùng)
 */
public final class DeathDomainRun extends SkillRun {

    private final double radius;
    private final String warning;

    public DeathDomainRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                          LivingEntity te, double power) {
        super(plugin, def, baseId, caster, target, te, power);
        ConfigurationSection p = def.params();
        radius = p.getDouble("radius", 15.0);
        warning = p.getString("warning", "");
    }

    @Override
    protected void onPhaseTick(PhaseDef.Id id) {
        if (id != PhaseDef.Id.CHARGE || warning.isEmpty() || phaseTick % 10 != 0) return;
        if (target.getWorld() == null) return;
        double r2 = radius * radius;
        for (Player p : target.getWorld().getPlayers()) {
            if (p.equals(caster) || p.getLocation().distanceSquared(target) > r2) continue;
            p.sendActionBar(ConfigManager.color(warning));
        }
    }
}
