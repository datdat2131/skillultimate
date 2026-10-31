package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Con rồng dựng hoàn toàn bằng particle: đầu + sừng + mắt, thân 14 đốt uốn lượn, hai cánh 4 ngón có màng vỗ,
 * và (tùy chọn) hơi thở lửa. Không có trạng thái: chỉ cần vị trí đầu, hướng bay và pha vỗ cánh.
 * Cấu hình (params.dragon): scale, body, wing, eye, fire (mỗi mục là một particle spec).
 */
public final class DragonModel {

    private static final int SEGMENTS = 14;

    private final ParticleSpec body;
    private final ParticleSpec wing;
    private final ParticleSpec eye;
    private final ParticleSpec fire;
    private final double scale;

    public DragonModel(ConfigurationSection s, Logger log) {
        if (s == null) s = new MemoryConfiguration();
        body = ParticleSpec.parse(s.getConfigurationSection("body"), log);
        wing = ParticleSpec.parse(s.getConfigurationSection("wing"), log);
        eye = ParticleSpec.parse(s.getConfigurationSection("eye"), log);
        fire = ParticleSpec.parse(s.getConfigurationSection("fire"), log);
        scale = Math.max(0.3, s.getDouble("scale", 1.2));
    }

    private static void spawn(QualityManager q, ParticleSpec sp, World w, double x, double y, double z) {
        if (sp != null && q.tryUse(1)) sp.spawn(w, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void line(QualityManager q, ParticleSpec sp, World w, Vector a, Vector b, double spacing) {
        if (sp == null) return;
        double len = a.distance(b);
        int n = Math.max(2, Math.min(40, (int) Math.ceil(len * q.density() / spacing)));
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            spawn(q, sp, w, a.getX() + (b.getX() - a.getX()) * f, a.getY() + (b.getY() - a.getY()) * f,
                    a.getZ() + (b.getZ() - a.getZ()) * f);
        }
    }

    /**
     * Vẽ một khung hình của con rồng.
     *
     * @param head    vị trí đầu rồng
     * @param dir     hướng bay
     * @param phase   pha vỗ cánh (radian, tăng dần theo thời gian)
     * @param breathe có phun lửa phía trước không
     */
    public void render(World w, QualityManager q, Location head, Vector dir, double phase, boolean breathe) {
        if (w == null) return;
        Vector d = dir.clone();
        if (d.lengthSquared() < 1.0E-6) d = new Vector(0, 0, 1);
        d.normalize();
        Vector side = d.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(d).normalize();
        double dens = q.density();
        Vector h = head.toVector();

        // ---- thân 14 đốt uốn lượn ----
        Vector shoulder = h.clone();
        for (int i = 0; i < SEGMENTS; i++) {
            double t = (double) i / (SEGMENTS - 1);
            Vector c = h.clone().subtract(d.clone().multiply(i * 0.75 * scale))
                    .add(side.clone().multiply(Math.sin(phase * 1.5 - i * 0.55) * 0.9 * scale * t))
                    .add(up.clone().multiply(Math.cos(phase * 1.5 - i * 0.55) * 0.25 * scale * t));
            if (i == 2) shoulder = c.clone();
            double r = scale * (0.5 * (1 - t) + 0.12);
            int m = Math.max(3, (int) Math.round(7 * dens * (1 - 0.5 * t)));
            for (int k = 0; k < m; k++) {
                double a = 2 * Math.PI * k / m + i * 0.4;
                Vector p = c.clone().add(side.clone().multiply(Math.cos(a) * r)).add(up.clone().multiply(Math.sin(a) * r));
                spawn(q, body, w, p.getX(), p.getY(), p.getZ());
            }
        }

        // ---- đầu, mõm, sừng, mắt ----
        int hn = Math.max(5, (int) Math.round(10 * dens));
        for (int k = 0; k < hn; k++) {
            double phi = Math.acos(1 - 2.0 * (k + 0.5) / hn);
            double th = Math.PI * (1 + Math.sqrt(5)) * (k + 0.5);
            Vector p = h.clone().add(side.clone().multiply(Math.sin(phi) * Math.cos(th) * 0.45 * scale))
                    .add(up.clone().multiply(Math.cos(phi) * 0.4 * scale))
                    .add(d.clone().multiply(Math.sin(phi) * Math.sin(th) * 0.45 * scale));
            spawn(q, body, w, p.getX(), p.getY(), p.getZ());
        }
        Vector mouth = h.clone().add(d.clone().multiply(0.95 * scale));
        line(q, body, w, h, mouth, 0.3);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vector hornBase = h.clone().add(up.clone().multiply(0.35 * scale)).add(side.clone().multiply(0.2 * scale * sgn));
            Vector hornTip = hornBase.clone().add(up.clone().multiply(0.5 * scale)).subtract(d.clone().multiply(0.5 * scale))
                    .add(side.clone().multiply(0.15 * scale * sgn));
            line(q, body, w, hornBase, hornTip, 0.25);
            Vector e = h.clone().add(d.clone().multiply(0.38 * scale)).add(up.clone().multiply(0.22 * scale))
                    .add(side.clone().multiply(0.22 * scale * sgn));
            spawn(q, eye, w, e.getX(), e.getY(), e.getZ());
        }

        // ---- hai cánh 4 ngón + màng ----
        double flap = Math.sin(phase * 1.2) * 0.8;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vector[] tips = new Vector[4];
            for (int j = 0; j < 4; j++) {
                double elev = 0.25 + flap + (j - 1.5) * 0.12;
                double len = (3.4 - j * 0.4) * scale;
                Vector dirJ = side.clone().multiply(sgn * Math.cos(elev)).add(up.clone().multiply(Math.sin(elev)));
                tips[j] = shoulder.clone().add(dirJ.multiply(len)).subtract(d.clone().multiply(0.55 * j * scale));
                line(q, wing, w, shoulder, tips[j], 0.4);
            }
            for (int j = 0; j < 3; j++) {
                line(q, wing, w, tips[j], tips[j + 1], 0.45);
                Vector m1 = shoulder.clone().add(tips[j].clone().subtract(shoulder).multiply(0.55));
                Vector m2 = shoulder.clone().add(tips[j + 1].clone().subtract(shoulder).multiply(0.55));
                line(q, wing, w, m1, m2, 0.5);
            }
        }

        // ---- hơi thở lửa ----
        if (breathe && fire != null) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            int fn = Math.max(3, (int) Math.round(10 * dens));
            for (int i = 0; i < fn; i++) {
                double dist = (1.0 + r.nextDouble() * 3.2) * scale;
                Vector p = mouth.clone().add(d.clone().multiply(dist))
                        .add(side.clone().multiply((r.nextDouble() - 0.5) * 0.12 * dist))
                        .add(up.clone().multiply((r.nextDouble() - 0.5) * 0.12 * dist));
                if (q.tryUse(1)) {
                    if (fire.directional()) fire.spawn(w, p.getX(), p.getY(), p.getZ(), 0, d.getX(), d.getY(), d.getZ(), 0.35);
                    else fire.spawn(w, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
