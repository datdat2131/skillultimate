package me.astrax.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Một lần thi triển skill: chạy timeline CAST → CHARGE → IMPACT → AFTER bằng MỘT task 1-tick duy nhất,
 * tự hủy khi kết thúc/bị gián đoạn/quá thời gian tối đa. Lớp con thêm cơ chế riêng qua các hook.
 */
public abstract class SkillRun {

    protected final AstraXUltimate plugin;
    protected final SkillDef def;
    protected final Player caster;
    protected final UUID casterId;
    protected final double power;
    protected Location target;
    protected LivingEntity targetEntity;

    protected PhaseDef.Id phase;
    protected int phaseTick;
    protected int totalTick;

    private final String baseId;
    private final long seed = ThreadLocalRandom.current().nextLong();
    private final List<PhaseDef.Id> order;
    private final Map<String, Integer> triggers = new HashMap<>();
    private final Map<UUID, float[]> shakePending = new HashMap<>();
    private int idx = -1;
    private BukkitTask task;
    private boolean finished;
    private boolean impactReached;
    private boolean impactFired;

    protected SkillRun(AstraXUltimate plugin, SkillDef def, String baseId, Player caster, Location target,
                       LivingEntity targetEntity, double power) {
        this.plugin = plugin;
        this.def = def;
        this.baseId = baseId;
        this.caster = caster;
        this.casterId = caster.getUniqueId();
        this.target = target;
        this.targetEntity = targetEntity;
        this.power = power;
        this.order = def.order();
    }

    // ---------------- Vòng đời ----------------
    public final void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    public final void cancel(String reason) {
        if (!finished) finish(true, reason);
    }

    private void finish(boolean cancelled, String reason) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        restoreShake();
        plugin.skills().unlock(casterId);
        try {
            onFinish();
        } finally {
            plugin.skills().onRunEnded(this, cancelled, reason);
        }
    }

    private void tick() {
        if (finished) return;
        if (!caster.isOnline() || caster.isDead()) {
            finish(true, "offline");
            return;
        }
        if (totalTick > plugin.getConfig().getInt("limits.max-run-ticks", 900)) {
            finish(true, "quá thời gian");
            return;
        }
        if (phase == null) {
            advance();
            if (finished) return;
        }
        PhaseDef pd = def.phase(phase);
        if (def.trackTarget() && targetEntity != null && targetEntity.isValid()) target = targetEntity.getLocation();
        if (phaseTick == 0) beginPhase(pd);
        runPhaseTick(pd);
        phaseTick++;
        totalTick++;
        if (isPhaseComplete(pd)) {
            onPhaseEnd(phase);
            advance();
        }
    }

    private void advance() {
        idx++;
        if (idx >= order.size()) {
            finish(false, "done");
            return;
        }
        phase = order.get(idx);
        phaseTick = 0;
    }

    private void beginPhase(PhaseDef pd) {
        triggers.clear();
        if (phase == PhaseDef.Id.IMPACT) impactReached = true;
        if (pd.lockMovement()) plugin.skills().lock(casterId);
        else plugin.skills().unlock(casterId);
        if (!pd.actionbar().isEmpty()) caster.sendActionBar(ConfigManager.color(pd.actionbar()));
        onPhaseStart(phase);
    }

    private void runPhaseTick(PhaseDef pd) {
        double prog = Math.min(1.0, (double) phaseTick / Math.max(1, pd.duration()));

        for (SoundSpec s : pd.sounds()) {
            int base = s.trigger() == null ? 0 : triggerTick(s.trigger());
            if (base < 0) continue;
            int lt = phaseTick - base - s.delay();
            if (lt < 0) continue;
            Location at = s.atTarget() ? target : caster.getLocation();
            if (s.interval() > 0) {
                if (lt % s.interval() == 0) s.play(at, prog);
            } else if (lt == 0) {
                s.play(at, prog);
            }
        }

        int eff = plugin.quality().layerInterval();
        for (VisualLayer l : pd.layers()) {
            int base = l.trigger() == null ? 0 : triggerTick(l.trigger());
            if (base < 0) continue;
            int lt = phaseTick - base - l.delay();
            if (lt < 0) continue;
            int dur = l.duration() > 0 ? l.duration() : Math.max(1, pd.duration() - l.delay());
            if (lt >= dur) continue;
            if (lt % Math.max(1, l.interval() * eff) != 0) continue;
            l.render(this, lt, dur);
        }

        PhaseDef.Pulse pu = pd.pulse();
        if (pu != null && phaseTick % pu.interval() == 0 && (pu.until() <= 0 || phaseTick < pu.until())) doPulse(pu);

        PhaseDef.Shake sh = pd.shake();
        if (sh != null && plugin.quality().shake()) {
            if (phaseTick < sh.duration()) {
                if (phaseTick % 2 == 0) shake(sh);
            } else if (phaseTick == sh.duration()) {
                restoreShake();
            }
        }

        if (phase == PhaseDef.Id.IMPACT && !impactFired && autoImpact() && phaseTick == pd.combatDelay()) {
            impactFired = true;
            onImpact();
        }
        onPhaseTick(phase);
    }

    private void doPulse(PhaseDef.Pulse pu) {
        Location c = pu.atCaster() ? caster.getLocation() : target;
        if (c == null) return;
        if (pu.combat() != null) {
            CombatEngine.Result r = plugin.combat().strike(caster, c, pu.radius(), pu.combat(), power, null);
            if (r.totalDamage > 0) feedback(r);
        }
        if (!pu.casterEffects().isEmpty() && caster.getWorld() == c.getWorld()
                && caster.getLocation().distanceSquared(c) <= pu.radius() * pu.radius()) {
            for (EffectSpec e : pu.casterEffects()) e.apply(caster);
        }
    }

    // ---------------- Rung màn hình (camera shake) ----------------
    private static float clampPitch(float p) {
        return Math.max(-89f, Math.min(89f, p));
    }

    private void shake(PhaseDef.Shake sh) {
        restoreShake();
        Location center = target != null ? target : caster.getLocation();
        if (center.getWorld() == null) return;
        double fall = 1.0 - (double) phaseTick / sh.duration();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : center.getWorld().getPlayers()) {
            double d = p.getLocation().distance(center);
            if (d > sh.radius()) continue;
            double inten = sh.intensity() * fall * (1.0 - 0.6 * d / sh.radius());
            float dy = (float) ((r.nextDouble() - 0.5) * 2 * inten);
            float dp = (float) ((r.nextDouble() - 0.5) * 2 * inten * 0.6);
            Location pl = p.getLocation();
            p.setRotation(pl.getYaw() + dy, clampPitch(pl.getPitch() + dp));
            shakePending.put(p.getUniqueId(), new float[]{dy, dp});
        }
    }

    private void restoreShake() {
        for (Map.Entry<UUID, float[]> e : shakePending.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;
            Location pl = p.getLocation();
            p.setRotation(pl.getYaw() - e.getValue()[0], clampPitch(pl.getPitch() - e.getValue()[1]));
        }
        shakePending.clear();
    }

    // ---------------- Trigger (ví dụ LAND) ----------------
    /** Kích hoạt các layer/sound có trigger tên này (tính từ tick hiện tại của giai đoạn). */
    protected final void releaseTrigger(String name) {
        triggers.putIfAbsent(name.toUpperCase(), phaseTick);
    }

    private int triggerTick(String name) {
        Integer v = triggers.get(name);
        return v == null ? -1 : v;
    }

    // ---------------- Chiến đấu ----------------
    protected final CombatEngine.Result strike(Location center, double radius, CombatProfile prof, Vector dir) {
        CombatEngine.Result r = plugin.combat().strike(caster, center, radius, prof, power, dir);
        feedback(r);
        return r;
    }

    /** Đòn đánh chính của giai đoạn IMPACT tại vị trí chỉ định. */
    protected final CombatEngine.Result strikeImpact(Location center) {
        PhaseDef pd = def.phase(PhaseDef.Id.IMPACT);
        if (pd == null || pd.combat() == null) return new CombatEngine.Result();
        return strike(center, pd.radius(), pd.combat(), caster.getLocation().getDirection());
    }

    protected final void feedback(CombatEngine.Result r) {
        if (r.hits <= 0) return;
        plugin.configs().actionBar(caster, r.crits > 0 ? "crit-feedback" : "hit-feedback",
                "targets", String.valueOf(r.hits), "damage", String.valueOf(Math.round(r.totalDamage)));
        caster.playSound(caster.getLocation(), r.crits > 0 ? "entity.player.attack.crit" : "entity.experience_orb.pickup",
                0.8f, 1.2f);
    }

    // ---------------- Hook cho lớp con ----------------
    protected void onPhaseStart(PhaseDef.Id id) {
    }

    protected void onPhaseTick(PhaseDef.Id id) {
    }

    protected void onPhaseEnd(PhaseDef.Id id) {
    }

    protected void onImpact() {
        strikeImpact(target);
    }

    /** false nếu lớp con tự quyết định thời điểm gây sát thương IMPACT (ví dụ Dragon Descent khi chạm đất). */
    protected boolean autoImpact() {
        return true;
    }

    protected boolean isPhaseComplete(PhaseDef pd) {
        return phaseTick >= pd.duration();
    }

    protected void onFinish() {
    }

    // ---------------- Truy cập cho VisualLayer & quản lý ----------------
    public Location anchor(VisualLayer.At at) {
        if (target == null) return caster.getLocation();
        return switch (at) {
            case CASTER -> caster.getLocation();
            case TARGET -> target.clone();
            case SKY -> target.clone().add(0, def.skyHeight(), 0);
            case HAND -> handLocation();
        };
    }

    private Location handLocation() {
        Location eye = caster.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1, 0, 0);
        right.normalize().multiply(0.35);
        return eye.clone().add(dir.multiply(0.6)).add(right).add(0, -0.35, 0);
    }

    public AstraXUltimate plugin() { return plugin; }
    public SkillDef def() { return def; }
    public String baseId() { return baseId; }
    public Player caster() { return caster; }
    public UUID casterId() { return casterId; }
    public Location target() { return target; }
    public long seed() { return seed; }
    public boolean reachedImpact() { return impactReached; }
    public boolean isFinished() { return finished; }
    public boolean interruptible() {
        return !finished && (phase == PhaseDef.Id.CAST || phase == PhaseDef.Id.CHARGE || phase == null);
    }
}
