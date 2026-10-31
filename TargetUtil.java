package me.astrax.ultimate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Ngắm mục tiêu bằng ray trace (không đụng tới block). */
public final class TargetUtil {

    private TargetUtil() {
    }

    /** Thực thể sống đang bị ngắm (trong tầm), null nếu không có. */
    public static LivingEntity aimEntity(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.6,
                e -> e instanceof LivingEntity le && !e.equals(p) && !le.isDead());
        if (r == null || !(r.getHitEntity() instanceof LivingEntity le)) return null;
        return le;
    }

    /**
     * Điểm trên mặt đất đang ngắm. Nếu ngắm trượt: allowMiss = true thì lấy điểm cuối tầm rồi thả xuống mặt đất,
     * false thì trả về null.
     */
    public static Location aimGround(Player p, double range, boolean allowMiss) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();
        RayTraceResult r = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitPosition() != null) {
            Vector v = r.getHitPosition();
            return new Location(w, v.getX(), v.getY() + 0.05, v.getZ());
        }
        if (!allowMiss) return null;
        Vector end = eye.toVector().add(dir.clone().multiply(range));
        return snapToGround(new Location(w, end.getX(), end.getY(), end.getZ()), 40);
    }

    /** Thả điểm xuống mặt đất gần nhất bên dưới (tối đa maxDrop block). */
    public static Location snapToGround(Location l, int maxDrop) {
        World w = l.getWorld();
        if (w == null) return l;
        Location c = l.clone();
        for (int i = 0; i < maxDrop; i++) {
            if (c.getBlockY() <= w.getMinHeight()) break;
            if (!c.getBlock().isPassable()) {
                c.setY(c.getBlockY() + 1.05);
                return c;
            }
            c.subtract(0, 1, 0);
        }
        return l;
    }
}
