package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Một lớp hiệu ứng hình ảnh cấu hình bằng YAML. Nhiều layer chồng lên nhau tạo chiều sâu.
 * <pre>
 * shock: {type: SHOCKWAVE, at: TARGET, particle: DUST, color: '#FFB060', size: 1.6,
 *         radius: 12, rings: 3, duration: 12, delay: 0, density: 1.0, interval: 1, fade: false,
 *         accent: {particle: FLAME}}
 * </pre>
 * Loại: CIRCLE (vòng ma pháp), AURA, SPIRAL, ORBIT (vòng năng lượng xoay quanh), CONVERGE (hút vào tâm),
 * SHOCKWAVE, SPHERE (cầu nổ), BEAM (cột sáng), LIGHTNING, DEBRIS, CRATER, SMOKE, FLASH, DOME, LINE.
 * Anchor (at): CASTER | TARGET | SKY | HAND.
 */
public final class VisualLayer {

    public enum Type { CIRCLE, AURA, SPIRAL, ORBIT, CONVERGE, SHOCKWAVE, SPHERE, BEAM, LIGHTNING, DEBRIS, CRATER, SMOKE, FLASH, DOME, LINE }

    public enum At {
        CASTER, TARGET, SKY, HAND;

        static At parse(String s, At def) {
            if (s == null) return def;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    private static final class Ctx {
        World w;
        QualityManager q;
        double dens;
        double prog;
        int t;
        double rot;
    }

    private final String name;
    private final Type type;
    private final At at;
    private final At to;
    private final double offsetY;
    private final double radius;
    private final double height;
    private final double density;
    private final double speed;
    private final double spread;
    private final double rotate;
    private final double jitter;
    private final double swirl;
    private final double grow;
    private final int delay;
    private final int duration;
    private final int interval;
    private final int sides;
    private final int rings;
    private final int strands;
    private final int branches;
    private final boolean fade;
    private final boolean flat;
    private final boolean sphere;
    private final String trigger;
    private final ParticleSpec spec;
    private final ParticleSpec accent;

    private VisualLayer(String name, Type type, ConfigurationSection s, ParticleSpec spec, ParticleSpec accent) {
        this.name = name;
        this.type = type;
        this.spec = spec;
        this.accent = accent;
        this.at = At.parse(s.getString("at"), At.TARGET);
        this.to = At.parse(s.getString("to"), At.TARGET);
        this.offsetY = s.getDouble("offset-y", 0.0);
        this.radius = s.getDouble("radius", 3.0);
        this.height = s.getDouble("height", 2.0);
        this.density = Math.max(0.05, s.getDouble("density", 1.0));
        this.speed = s.getDouble("speed", 1.0);
        this.spread = s.getDouble("spread", 0.0);
        this.rotate = s.getDouble("rotate", 0.0);
        this.jitter = s.getDouble("jitter", type == Type.LIGHTNING ? 0.9 : 0.0);
        this.swirl = s.getDouble("swirl", 0.0);
        this.grow = s.getDouble("grow", 0.0);
        this.delay = Math.max(0, s.getInt("delay", 0));
        this.duration = Math.max(0, s.getInt("duration", 0));
        int defInterval = switch (type) {
            case CIRCLE, DOME, BEAM -> 2;
            case CRATER -> 4;
            default -> 1;
        };
        this.interval = Math.max(1, s.getInt("interval", defInterval));
        this.sides = s.getInt("sides", 6);
        this.rings = s.getInt("rings", 2);
        this.strands = s.getInt("strands", 0);
        this.branches = s.getInt("branches", 3);
        this.fade = s.getBoolean("fade", false);
        this.flat = s.getBoolean("flat", false);
        this.sphere = s.getBoolean("sphere", false);
        String tr = s.getString("trigger");
        this.trigger = tr == null ? null : tr.toUpperCase(Locale.ROOT);
    }

    public static VisualLayer parse(String name, ConfigurationSection s, Logger log) {
        if (s == null) return null;
        Type type;
        try {
            type = Type.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Layer '" + name + "': type không hợp lệ: " + s.getString("type"));
            return null;
        }
        ParticleSpec spec = ParticleSpec.parse(s, log);
        if (spec == null) {
            log.warning("Layer '" + name + "': thiếu hoặc sai particle.");
            return null;
        }
        return new VisualLayer(name, type, s, spec, ParticleSpec.parse(s.getConfigurationSection("accent"), log));
    }

    public String name() { return name; }
    public int delay() { return delay; }
    public int duration() { return duration; }
    public int interval() { return interval; }
    public String trigger() { return trigger; }

    // ---------------- Vẽ ----------------
    public void render(SkillRun run, int t, int life) {
        Location a = run.anchor(at);
        if (a == null || a.getWorld() == null) return;
        Ctx c = new Ctx();
        c.w = a.getWorld();
        c.q = run.plugin().quality();
        c.t = t;
        c.prog = Math.min(1.0, (t + 1.0) / Math.max(1, life));
        c.dens = Math.max(0.05, density * c.q.density() * (fade ? Math.max(0.08, 1.0 - c.prog * 0.9) : 1.0));
        c.rot = Math.toRadians(rotate * t);
        double ax = a.getX();
        double ay = a.getY() + offsetY;
        double az = a.getZ();
        switch (type) {
            case CIRCLE -> circle(c, ax, ay, az);
            case AURA -> aura(c, ax, ay, az);
            case SPIRAL -> spiral(c, ax, ay, az);
            case ORBIT -> orbit(c, ax, ay, az);
            case CONVERGE -> converge(c, ax, ay, az);
            case SHOCKWAVE -> shockwave(c, ax, ay, az);
            case SPHERE -> sphereBurst(c, ax, ay, az);
            case BEAM -> beam(c, ax, ay, az);
            case LIGHTNING -> lightning(c, run, ax, ay, az);
            case DEBRIS -> debris(c, ax, ay, az);
            case CRATER -> crater(c, run, ax, ay, az);
            case SMOKE -> smoke(c, ax, ay, az);
            case FLASH -> flash(c, ax, ay, az);
            case DOME -> dome(c, ax, ay, az);
            case LINE -> lineLayer(c, run, a);
        }
    }

    // ---------------- Tiện ích vẽ ----------------
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void dot(Ctx c, ParticleSpec sp, double x, double y, double z) {
        if (sp == null || !c.q.tryUse(1)) return;
        sp.spawn(c.w, x, y, z, 1, 0, 0, 0, spread);
    }

    /** Particle có vận tốc (count = 0 → offset là hướng, extra là tốc độ). Dust... thì spawn thường. */
    private void vel(Ctx c, ParticleSpec sp, double x, double y, double z, double dx, double dy, double dz, double spd) {
        if (sp == null || !c.q.tryUse(1)) return;
        if (sp.directional()) sp.spawn(c.w, x, y, z, 0, dx, dy, dz, spd);
        else sp.spawn(c.w, x, y, z, 1, 0, 0, 0, 0);
    }

    private void ring(Ctx c, ParticleSpec sp, double cx, double cy, double cz, double r, double start, double spacing) {
        if (r <= 0.05) return;
        int n = clamp((int) Math.ceil(2 * Math.PI * r * c.dens / spacing), 8, 300);
        for (int i = 0; i < n; i++) {
            double a = start + 2 * Math.PI * i / n;
            dot(c, sp, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
        }
    }

    private void seg(Ctx c, ParticleSpec sp, double x1, double y1, double z1, double x2, double y2, double z2, double spacing) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = clamp((int) Math.ceil(len * c.dens / spacing), 2, 300);
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            dot(c, sp, x1 + dx * f, y1 + dy * f, z1 + dz * f);
        }
    }

    // ---------------- Các loại layer ----------------
    private void circle(Ctx c, double ax, double ay, double az) {
        double R = radius * (grow > 0 ? Math.min(1.0, (c.t + 1.0) / grow) : 1.0);
        int rr = Math.max(1, rings);
        for (int k = 0; k < rr; k++) {
            ring(c, spec, ax, ay + 0.05, az, R * (1.0 - k * 0.1), c.rot * (k % 2 == 0 ? 1 : -1), 0.4);
        }
        ParticleSpec acc = accent != null ? accent : spec;
        int sd = Math.max(3, sides);
        double sr = R * 0.82;
        int step = Math.max(1, (sd - 1) / 2);
        for (int i = 0; i < sd; i++) {
            double a1 = c.rot + 2 * Math.PI * i / sd;
            double a2 = c.rot + 2 * Math.PI * ((i + step) % sd) / sd;
            seg(c, acc, ax + Math.cos(a1) * sr, ay + 0.05, az + Math.sin(a1) * sr,
                    ax + Math.cos(a2) * sr, ay + 0.05, az + Math.sin(a2) * sr, 0.45);
        }
        int runes = sd * 2;
        for (int i = 0; i < runes; i++) {
            double ang = -c.rot * 0.5 + 2 * Math.PI * i / runes;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            seg(c, acc, ax + ca * R * 0.86, ay + 0.05, az + sa * R * 0.86,
                    ax + ca * R * 0.97, ay + 0.05, az + sa * R * 0.97, 0.4);
        }
    }

    private void aura(Ctx c, double ax, double ay, double az) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(1, (int) Math.round(6 * c.dens));
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * 2 * Math.PI;
            double rr = radius * (0.35 + 0.65 * r.nextDouble());
            vel(c, spec, ax + Math.cos(ang) * rr, ay + r.nextDouble() * height, az + Math.sin(ang) * rr,
                    0, 1, 0, 0.03 * speed);
        }
    }

    private void spiral(Ctx c, double ax, double ay, double az) {
        int strandsN = strands > 0 ? strands : 2;
        int m = Math.max(6, (int) Math.round(14 * c.dens));
        double hh = height * c.prog;
        for (int k = 0; k < strandsN; k++) {
            for (int i = 0; i <= m; i++) {
                double f = (double) i / m;
                double ang = c.rot + k * 2 * Math.PI / strandsN + f * hh * 1.6 + c.t * 0.35;
                dot(c, spec, ax + Math.cos(ang) * radius, ay + hh * f, az + Math.sin(ang) * radius);
            }
        }
    }

    private void orbit(Ctx c, double ax, double ay, double az) {
        int rr = Math.max(1, rings);
        for (int k = 0; k < rr; k++) {
            double tilt = Math.PI * k / rr + 0.4;
            double phi = c.rot * (k % 2 == 0 ? 1 : -1) + k * 1.1;
            double ph = k * Math.PI / rr;
            int n = clamp((int) Math.round(2 * Math.PI * radius * c.dens / 0.45), 8, 200);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n + phi;
                double x0 = radius * Math.cos(a), z0 = radius * Math.sin(a);
                double y1 = -z0 * Math.sin(tilt), z1 = z0 * Math.cos(tilt);
                double x2 = x0 * Math.cos(ph) + z1 * Math.sin(ph);
                double z2 = -x0 * Math.sin(ph) + z1 * Math.cos(ph);
                dot(c, spec, ax + x2, ay + y1, az + z2);
            }
        }
    }

    private void converge(Ctx c, double ax, double ay, double az) {
        int n = clamp((int) Math.round(32 * c.dens), 3, 220);
        double rate = 0.05 * Math.max(0.1, speed);
        for (int i = 0; i < n; i++) {
            double phase = (i * 0.6180339887 + c.t * rate) % 1.0;
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / n);
            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5) + swirl * phase + c.rot;
            double d = radius * (1 - phase);
            dot(c, spec, ax + d * Math.sin(phi) * Math.cos(th),
                    ay + d * Math.cos(phi) * (flat ? 0.25 : 1.0),
                    az + d * Math.sin(phi) * Math.sin(th));
        }
    }

    private void shockwave(Ctx c, double ax, double ay, double az) {
        int rr = Math.max(1, rings);
        double e = 1 - Math.pow(1 - c.prog, 2);
        for (int k = 0; k < rr; k++) {
            double r = radius * e - k * 0.9;
            if (r <= 0.3) continue;
            ring(c, spec, ax, ay + 0.15, az, r, k * 0.5, 0.35);
            if (height > 0) {
                int levels = Math.max(1, (int) (height / 0.8));
                for (int l = 1; l <= levels; l++) {
                    ring(c, accent != null ? accent : spec, ax, ay + 0.15 + l * 0.8 * (1 - c.prog * 0.5), az, r, k, 0.9);
                }
            }
        }
    }

    private void sphereBurst(Ctx c, double ax, double ay, double az) {
        double e = 1 - Math.pow(1 - c.prog, 2);
        double r = radius * e;
        int n = clamp((int) Math.round(70 * c.dens), 6, 260);
        for (int i = 0; i < n; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / n);
            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5) + c.rot;
            double dx = Math.sin(phi) * Math.cos(th), dy = Math.cos(phi), dz = Math.sin(phi) * Math.sin(th);
            vel(c, spec, ax + dx * r, ay + dy * r, az + dz * r, dx, dy, dz, 0.1 * speed);
        }
    }

    private void beam(Ctx c, double ax, double ay, double az) {
        double r = radius * (1 - 0.3 * c.prog);
        int levels = Math.max(2, (int) (height / 1.0));
        for (int l = 0; l < levels; l++) {
            ring(c, spec, ax, ay + height * l / (levels - 1), az, r, c.rot + l * 0.5, 1.1);
        }
        seg(c, accent != null ? accent : spec, ax, ay, az, ax, ay + height, az, 0.4);
    }

    private void lightning(Ctx c, SkillRun run, double ax, double ay, double az) {
        double top = ay + height;
        int segs = Math.max(5, (int) Math.round(height / 1.4));
        Random rnd = new Random(run.seed() * 31 + (c.t / 2));
        double[] px = new double[segs + 1], py = new double[segs + 1], pz = new double[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double f = (double) i / segs;
            double j = (i == 0 || i == segs) ? 0 : jitter;
            py[i] = top - (top - ay) * f;
            px[i] = ax + (rnd.nextDouble() - 0.5) * 2 * j;
            pz[i] = az + (rnd.nextDouble() - 0.5) * 2 * j;
        }
        int strandsN = Math.max(1, strands > 0 ? strands : 2);
        for (int s = 0; s < strandsN; s++) {
            double ox = s == 0 ? 0 : (rnd.nextDouble() - 0.5) * 0.35;
            double oz = s == 0 ? 0 : (rnd.nextDouble() - 0.5) * 0.35;
            for (int i = 0; i < segs; i++) {
                seg(c, spec, px[i] + ox, py[i], pz[i] + oz, px[i + 1] + ox, py[i + 1], pz[i + 1] + oz, 0.3);
            }
        }
        if (accent != null) {
            for (int i = 0; i < segs; i++) seg(c, accent, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.45);
        }
        for (int b = 0; b < Math.max(0, branches); b++) {
            int i = 2 + rnd.nextInt(Math.max(1, segs - 3));
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double len = 2 + rnd.nextDouble() * 3;
            seg(c, spec, px[i], py[i], pz[i], px[i] + Math.cos(ang) * len, py[i] - len * 0.6, pz[i] + Math.sin(ang) * len, 0.4);
        }
    }

    private void debris(Ctx c, double ax, double ay, double az) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(1, (int) Math.round(16 * c.dens));
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * 2 * Math.PI;
            double near = radius * 0.4 * r.nextDouble();
            vel(c, spec, ax + Math.cos(ang) * near, ay + 0.2, az + Math.sin(ang) * near,
                    Math.cos(ang) * 0.45, 0.5 + r.nextDouble() * 0.9, Math.sin(ang) * 0.45, 0.6 * speed);
        }
    }

    private void crater(Ctx c, SkillRun run, double ax, double ay, double az) {
        double R = radius;
        int ringsN = Math.max(2, (int) (R / 1.7));
        for (int k = 1; k <= ringsN; k++) ring(c, spec, ax, ay + 0.1, az, R * k / ringsN, k * 0.7, 1.3);
        ParticleSpec acc = accent != null ? accent : spec;
        int rim = clamp((int) Math.round(2 * Math.PI * R * c.dens / 0.7), 12, 260);
        double ph = run.seed() % 7;
        for (int i = 0; i < rim; i++) {
            double ang = 2 * Math.PI * i / rim;
            double jag = 1.0 + 0.12 * Math.sin(ang * 7 + ph) + 0.08 * Math.sin(ang * 13);
            dot(c, acc, ax + Math.cos(ang) * R * jag, ay + 0.15, az + Math.sin(ang) * R * jag);
        }
        int cracks = Math.max(3, strands > 0 ? strands : 6);
        Random rnd = new Random(run.seed());
        for (int k = 0; k < cracks; k++) {
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double len = R * (0.55 + 0.4 * rnd.nextDouble());
            double mx = Math.cos(ang) * len * 0.5 + (rnd.nextDouble() - 0.5) * 1.2;
            double mz = Math.sin(ang) * len * 0.5 + (rnd.nextDouble() - 0.5) * 1.2;
            seg(c, acc, ax, ay + 0.12, az, ax + mx, ay + 0.12, az + mz, 0.6);
            seg(c, acc, ax + mx, ay + 0.12, az + mz, ax + Math.cos(ang) * len, ay + 0.12, az + Math.sin(ang) * len, 0.6);
        }
    }

    private void smoke(Ctx c, double ax, double ay, double az) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(1, (int) Math.round(5 * c.dens));
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * 2 * Math.PI;
            double rr = radius * Math.sqrt(r.nextDouble());
            vel(c, spec, ax + Math.cos(ang) * rr, ay + 0.2 + r.nextDouble() * 0.5, az + Math.sin(ang) * rr,
                    0, 1, 0, 0.12 * speed);
        }
    }

    private void flash(Ctx c, double ax, double ay, double az) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = clamp((int) Math.round(120 * c.dens), 10, 300);
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble() * 2 - 1;
            double th = r.nextDouble() * 2 * Math.PI;
            double s = Math.sqrt(1 - u * u);
            double dx = s * Math.cos(th), dy = u, dz = s * Math.sin(th);
            double d = radius * (0.25 + 0.75 * r.nextDouble());
            vel(c, spec, ax + dx * d, ay + 1 + dy * d, az + dz * d, dx, dy, dz, 0.25 * speed);
        }
        if (accent != null && c.t == 0 && c.q.tryUse(1)) {
            accent.spawn(c.w, ax, ay + 1, az, 3, 0.3, 0.3, 0.3, 0);
        }
    }

    private void dome(Ctx c, double ax, double ay, double az) {
        double e = grow > 0 ? Math.min(1.0, (c.t + 1.0) / grow) : 1.0;
        e = 1 - (1 - e) * (1 - e);
        double R = radius * e;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = clamp((int) Math.round(55 * c.dens * Math.max(1.0, radius / 12.0)), 8, 260);
        for (int i = 0; i < n; i++) {
            double u = sphere ? r.nextDouble() * 2 - 1 : r.nextDouble();
            double th = r.nextDouble() * 2 * Math.PI;
            double s = Math.sqrt(1 - u * u);
            ParticleSpec sp = (accent != null && r.nextDouble() < 0.25) ? accent : spec;
            dot(c, sp, ax + s * Math.cos(th) * R, ay + u * R, az + s * Math.sin(th) * R);
        }
    }

    private void lineLayer(Ctx c, SkillRun run, Location a) {
        Location b = run.anchor(to);
        if (b == null) return;
        double bx = b.getX(), by = b.getY() + 1.0, bz = b.getZ();
        double dx = bx - a.getX(), dy = by - a.getY(), dz = bz - a.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = clamp((int) Math.ceil(len * c.dens / 0.5), 2, 160);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            double j = (i == 0 || i == n) ? 0 : jitter;
            dot(c, spec, a.getX() + dx * f + (r.nextDouble() - 0.5) * 2 * j,
                    a.getY() + dy * f + (r.nextDouble() - 0.5) * 2 * j,
                    a.getZ() + dz * f + (r.nextDouble() - 0.5) * 2 * j);
        }
    }
}
