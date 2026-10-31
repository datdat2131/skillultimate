package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * DRAGON DESCENT – CAST: bật lên không; CHARGE: lơ lửng, rồng particle hiện sau lưng; IMPACT: lao xuống,
 * chạm đất → shockwave (layer có trigger: LAND) rồi một con rồng particle lao ra theo hướng nhìn, quét sát thương.
 * params: launch, dive-speed, dive-max-ticks, dragon: {...},
 *         blast: {range, ticks, radius, sound, combat: {...}}
 */
public final class DragonDescentRun extends SkillRun {

    private final double launch;
    private final double diveSpeed;
    private final int diveMax;
    private final DragonModel model;
    private final double blastRange;
    private final int blastTicks;
    private final double blastRadius;
    private final String blastSound;
    private final CombatProfile blastCombat;

    private boolean gravityOff;
    private boolean landed;
    private int landTick;
    private Vector diveDir = new Vector(0, -1, 0);
    private Vector blastDir = new Vector(0, 0, 1);
    private Location blastStart;
    private final Set<UUID> blastHit = new HashSet<>();

    public DragonDescentRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                            LivingEntity te, double power) {
        super(plugin, def, baseId, caster, target, te, power);
        ConfigurationSection p = def.params();
        launch = p.getDouble("launch", 1.3);
        diveSpeed = p.getDouble("dive-speed", 1.9);
        diveMax = Math.max(10, p.getInt("dive-max-ticks", 50));
        model = new DragonModel(p.getConfigurationSection("dragon"), plugin.getLogger());
        blastRange = p.getDouble("blast.range", 28.0);
        blastTicks = Math.max(4, p.getInt("blast.ticks", 22));
        blastRadius = p.getDouble("blast.radius", 3.2);
        blastSound = p.getString("blast.sound", "entity.ender_dragon.growl");
        blastCombat = CombatProfile.parse(p.getConfigurationSection("blast.combat"));
    }

    private Vector horizontalFacing() {
        Vector f = caster.getLocation().getDirection().setY(0);
        if (f.lengthSquared() < 1.0E-4) f = new Vector(0, 0, 1);
        return f.normalize();
    }

    @Override
    protected boolean autoImpact() {
        return false;
    }

    @Override
    protected void onPhaseStart(PhaseDef.Id id) {
        switch (id) {
            case CAST -> {
                caster.setFallDistance(0f);
                caster.setVelocity(new Vector(0, launch, 0));
            }
            case CHARGE -> {
                caster.setGravity(false);
                gravityOff = true;
                caster.setVelocity(new Vector(0, 0, 0));
            }
            case IMPACT -> {
                caster.setGravity(true);
                gravityOff = false;
                Vector to = target.toVector().subtract(caster.getLocation().toVector());
                diveDir = to.lengthSquared() < 1.0 ? new Vector(0, -1, 0) : to.normalize();
                if (diveDir.getY() > -0.35) {
                    diveDir.setY(-0.6);
                    diveDir.normalize();
                }
                caster.setVelocity(diveDir.clone().multiply(diveSpeed));
            }
            default -> {
            }
        }
    }

    @Override
    protected void onPhaseTick(PhaseDef.Id id) {
        World w = caster.getWorld();
        QualityManager q = plugin.quality();
        switch (id) {
            case CAST -> caster.setFallDistance(0f);
            case CHARGE -> {
                caster.setFallDistance(0f);
                caster.setVelocity(new Vector(0, 0.02, 0));
                Vector f = horizontalFacing();
                Location head = caster.getLocation().add(0, 3.2, 0).subtract(f.clone().multiply(4.5));
                model.render(w, q, head, f, totalTick * 0.35, false);
            }
            case IMPACT -> {
                caster.setFallDistance(0f);
                if (!landed) {
                    caster.setVelocity(diveDir.clone().multiply(diveSpeed));
                    Location head = caster.getLocation().add(0, 1.2, 0).subtract(diveDir.clone().multiply(2.4));
                    model.render(w, q, head, diveDir, totalTick * 0.45, true);
                    boolean ground = caster.isOnGround() && phaseTick > 2;
                    boolean near = caster.getLocation().distanceSquared(target) < 4.0;
                    if (ground || near || phaseTick >= diveMax) land();
                } else {
                    blast(w, q);
                }
            }
            default -> {
            }
        }
    }

    private void land() {
        landed = true;
        landTick = phaseTick;
        caster.setVelocity(new Vector(0, 0, 0));
        caster.setFallDistance(0f);
        releaseTrigger("LAND");
        target = caster.getLocation().clone();
        blastDir = horizontalFacing();
        blastStart = target.clone().add(0, 1.3, 0);
        strikeImpact(target);
    }

    private void blast(World w, QualityManager q) {
        int b = phaseTick - landTick - 1;
        if (b < 0 || b >= blastTicks || blastStart == null) return;
        double f = (b + 1.0) / blastTicks;
        Location head = blastStart.clone().add(blastDir.clone().multiply(blastRange * f));
        model.render(w, q, head, blastDir, totalTick * 0.45, true);
        if (b == 0) {
            w.playSound(head, SoundSpec.soundKey(blastSound), SoundCategory.MASTER, 3.0f, 0.9f);
        }
        if (blastCombat != null) {
            CombatEngine.Result r = plugin.combat().strike(caster, head, blastRadius, blastCombat, power, blastDir, blastHit);
            feedback(r);
        }
        if (b == blastTicks - 1) {
            if (q.tryUse(1)) w.spawnParticle(Particle.EXPLOSION_EMITTER, head, 1);
            w.playSound(head, "entity.generic.explode", SoundCategory.MASTER, 3.0f, 0.7f);
        }
    }

    @Override
    protected boolean isPhaseComplete(PhaseDef pd) {
        if (pd.id() == PhaseDef.Id.IMPACT) {
            return landed ? phaseTick >= landTick + blastTicks + 6 : phaseTick >= pd.duration();
        }
        return phaseTick >= pd.duration();
    }

    @Override
    protected void onFinish() {
        caster.setGravity(true);
        caster.setFallDistance(0f);
        if (gravityOff || !landed) {
            PotionEffectType t = EffectSpec.potion("slow_falling");
            if (t != null) caster.addPotionEffect(new PotionEffect(t, 80, 0, false, false, false));
        }
    }
}
