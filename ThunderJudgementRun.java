package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * THUNDER JUDGEMENT / DIVINE THUNDER – sét giáng xuống mục tiêu rồi nhảy (chain) sang các mục tiêu gần đó.
 * params: chain: {jumps, range, interval, damage-factor, arc: {particle...}, arc-core: {particle...}, sound}
 */
public final class ThunderJudgementRun extends SkillRun {

    private final int jumps;
    private final double range;
    private final int interval;
    private final double factor;
    private final ParticleSpec arc;
    private final ParticleSpec arcCore;
    private final String arcSound;

    private final Set<UUID> hitSet = new HashSet<>();
    private Location chainFrom;
    private int jumpIndex;
    private int nextJumpTick = -1;

    public ThunderJudgementRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                               LivingEntity te, double power) {
        super(plugin, def, baseId, caster, target, te, power);
        ConfigurationSection c = def.params().getConfigurationSection("chain");
        if (c == null) c = new org.bukkit.configuration.MemoryConfiguration();
        jumps = Math.max(0, Math.min(15, c.getInt("jumps", 4)));
        range = c.getDouble("range", 8.0);
        interval = Math.max(1, c.getInt("interval", 4));
        factor = c.getDouble("damage-factor", 0.75);
        arc = ParticleSpec.parse(c.getConfigurationSection("arc"), plugin.getLogger());
        arcCore = ParticleSpec.parse(c.getConfigurationSection("arc-core"), plugin.getLogger());
        arcSound = c.getString("sound", "entity.lightning_bolt.impact");
    }

    @Override
    protected void onImpact() {
        CombatEngine.Result r = strikeImpact(target);
        for (LivingEntity le : r.targets) hitSet.add(le.getUniqueId());
        chainFrom = !r.targets.isEmpty() ? r.targets.get(0).getLocation() : target.clone();
        if (jumps > 0) nextJumpTick = phaseTick + interval;
    }

    @Override
    protected void onPhaseTick(PhaseDef.Id id) {
        if (id != PhaseDef.Id.IMPACT || nextJumpTick < 0 || jumpIndex >= jumps || phaseTick < nextJumpTick) return;
        PhaseDef pd = def.phase(PhaseDef.Id.IMPACT);
        if (pd == null || pd.combat() == null || chainFrom == null) {
            nextJumpTick = -1;
            return;
        }
        LivingEntity next = null;
        for (LivingEntity le : plugin.combat().findTargets(caster, chainFrom, range, pd.combat())) {
            if (!hitSet.contains(le.getUniqueId())) {
                next = le;
                break;
            }
        }
        if (next == null) {
            nextJumpTick = -1;
            return;
        }
        jumpIndex++;
        Location to = next.getLocation().add(0, next.getHeight() / 2, 0);
        drawArc(chainFrom.clone().add(0, 1, 0), to);
        CombatEngine.Result r = new CombatEngine.Result();
        plugin.combat().hit(caster, next, chainFrom, pd.combat(), power, Math.pow(factor, jumpIndex),
                caster.getLocation().getDirection(), r);
        feedback(r);
        hitSet.add(next.getUniqueId());
        chainFrom = next.getLocation();
        World w = chainFrom.getWorld();
        if (w != null) w.playSound(chainFrom, SoundSpec.soundKey(arcSound), org.bukkit.SoundCategory.MASTER, 1.4f,
                1.2f + 0.08f * jumpIndex);
        nextJumpTick = phaseTick + interval;
    }

    /** Vẽ tia sét zigzag giữa hai điểm. */
    private void drawArc(Location a, Location b) {
        World w = a.getWorld();
        if (w == null || w != b.getWorld()) return;
        QualityManager q = plugin.quality();
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segs = Math.max(3, (int) (len / 1.2));
        Random rnd = new Random(seed() + jumpIndex * 7919L);
        double px = a.getX(), py = a.getY(), pz = a.getZ();
        for (int i = 1; i <= segs; i++) {
            double f = (double) i / segs;
            double j = i == segs ? 0 : 0.55;
            double nx = a.getX() + dx * f + (rnd.nextDouble() - 0.5) * 2 * j;
            double ny = a.getY() + dy * f + (rnd.nextDouble() - 0.5) * 2 * j;
            double nz = a.getZ() + dz * f + (rnd.nextDouble() - 0.5) * 2 * j;
            int pts = Math.max(2, (int) Math.round(Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py) + (nz - pz) * (nz - pz))
                    * 4 * q.density()));
            for (int k = 0; k <= pts; k++) {
                double t = (double) k / pts;
                double x = px + (nx - px) * t, y = py + (ny - py) * t, z = pz + (nz - pz) * t;
                if (arc != null && q.tryUse(1)) arc.spawn(w, x, y, z, 1, 0, 0, 0, 0);
                if (arcCore != null && k % 2 == 0 && q.tryUse(1)) arcCore.spawn(w, x, y, z, 1, 0, 0, 0, 0);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
    }
}
