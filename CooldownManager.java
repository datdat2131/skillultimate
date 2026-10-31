package me.astrax.ultimate;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Hồi chiêu theo thời gian thực. Hiển thị: ActionBar khi thử dùng lúc đang hồi, BossBar tiến độ của skill vừa dùng,
 * thông báo + âm thanh khi sẵn sàng. Cooldown vẫn được giữ khi người chơi thoát (chống thoát vào để reset).
 */
public final class CooldownManager {

    private record Cd(String id, String name, long start, long end) {}

    private final AstraXUltimate plugin;
    private final Map<UUID, Map<String, Cd>> map = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public CooldownManager(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    public void start(Player p, String id, String displayName, double seconds) {
        if (seconds <= 0) return;
        long now = System.currentTimeMillis();
        map.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(id, new Cd(id, displayName, now, now + (long) (seconds * 1000)));
    }

    /** Số giây còn lại (0 nếu đã sẵn sàng). */
    public double remaining(UUID uuid, String id) {
        Map<String, Cd> m = map.get(uuid);
        if (m == null) return 0;
        Cd cd = m.get(id);
        if (cd == null) return 0;
        return Math.max(0, (cd.end() - System.currentTimeMillis()) / 1000.0);
    }

    /** Rút ngắn cooldown còn tối đa maxSeconds (dùng khi skill bị hủy trước Impact). */
    public void shorten(UUID uuid, String id, double maxSeconds) {
        Map<String, Cd> m = map.get(uuid);
        if (m == null || !m.containsKey(id)) return;
        Cd cd = m.get(id);
        long limit = System.currentTimeMillis() + (long) (maxSeconds * 1000);
        if (cd.end() > limit) m.put(id, new Cd(cd.id(), cd.name(), cd.start(), limit));
    }

    public void reset(UUID uuid) {
        map.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) hideBar(p);
    }

    // ---------------- Hiển thị ----------------
    public static String bar(double progress, int len) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * len);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < len; i++) {
            if (i == filled) sb.append("&7");
            sb.append('█');
        }
        return sb.toString();
    }

    private static String fmt(double sec) {
        return String.format(Locale.US, "%.1f", sec);
    }

    /** ActionBar khi người chơi thử dùng skill đang hồi. */
    public void showCooling(Player p, String id, String name) {
        Map<String, Cd> m = map.get(p.getUniqueId());
        Cd cd = m == null ? null : m.get(id);
        if (cd == null) return;
        double rem = Math.max(0, (cd.end() - System.currentTimeMillis()) / 1000.0);
        double prog = 1.0 - rem / Math.max(0.001, (cd.end() - cd.start()) / 1000.0);
        plugin.configs().actionBar(p, "cooldown-bar", "skill", name, "time", fmt(rem), "bar", bar(prog, 20));
        p.playSound(p.getLocation(), "block.note_block.bass", 0.6f, 0.6f);
    }

    /** Gọi mỗi 2 tick. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<String, Cd>>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Cd>> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Map<String, Cd> m = e.getValue();
            Cd latest = null;
            for (Iterator<Cd> ci = m.values().iterator(); ci.hasNext(); ) {
                Cd cd = ci.next();
                if (cd.end() <= now) {
                    ci.remove();
                    if (p != null) notifyReady(p, cd);
                } else if (latest == null || cd.start() > latest.start()) {
                    latest = cd;
                }
            }
            if (m.isEmpty()) {
                if (p != null) hideBar(p);
                it.remove();
                continue;
            }
            if (p != null && latest != null) updateBar(p, latest, now);
        }
    }

    private void notifyReady(Player p, Cd cd) {
        plugin.configs().actionBar(p, "skill-ready", "skill", cd.name());
        String snd = plugin.getConfig().getString("cooldown.ready-sound", "entity.experience_orb.pickup");
        if (snd != null && !snd.isEmpty()) p.playSound(p.getLocation(), SoundSpec.soundKey(snd), 0.8f, 1.5f);
    }

    private void updateBar(Player p, Cd cd, long now) {
        if (!plugin.getConfig().getBoolean("cooldown.show-bossbar", true)) return;
        double total = Math.max(1, cd.end() - cd.start());
        float prog = (float) Math.max(0, Math.min(1, 1.0 - (cd.end() - now) / total));
        Component name = ConfigManager.color(plugin.configs().raw("bossbar-title", "skill", cd.name(),
                "time", fmt(Math.max(0, (cd.end() - now) / 1000.0))));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, prog, color(), overlay());
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(prog);
        }
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(plugin.getConfig().getString("cooldown.bar-color", "PURPLE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.PURPLE;
        }
    }

    private BossBar.Overlay overlay() {
        try {
            return BossBar.Overlay.valueOf(plugin.getConfig().getString("cooldown.bar-overlay", "PROGRESS").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    /** Khi người chơi thoát: chỉ gỡ BossBar, giữ nguyên cooldown. */
    public void onQuit(Player p) {
        hideBar(p);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) hideBar(p);
        bars.clear();
        map.clear();
    }
}
