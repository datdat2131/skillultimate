package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Một âm thanh trong timeline của giai đoạn:
 * <pre>
 * rise: {key: block.beacon.ambient, volume: 1.5, pitch: 0.5, pitch-end: 2.0, interval: 4, delay: 0, at: CASTER, trigger: LAND}
 * </pre>
 * interval &gt; 0: lặp lại mỗi N tick suốt giai đoạn (pitch chạy từ pitch đến pitch-end → âm thanh tăng dần).
 * trigger: chỉ bắt đầu khi skill gọi releaseTrigger (ví dụ LAND khi Dragon Descent chạm đất).
 */
public final class SoundSpec {

    private final String key;
    private final float volume;
    private final float pitch;
    private final float pitchEnd;
    private final int delay;
    private final int interval;
    private final boolean atTarget;
    private final String trigger;

    private SoundSpec(ConfigurationSection s) {
        this.key = s.getString("key");
        this.volume = (float) s.getDouble("volume", 1.0);
        this.pitch = (float) s.getDouble("pitch", 1.0);
        this.pitchEnd = s.contains("pitch-end") ? (float) s.getDouble("pitch-end") : Float.NaN;
        this.delay = Math.max(0, s.getInt("delay", 0));
        this.interval = Math.max(0, s.getInt("interval", 0));
        this.atTarget = "TARGET".equalsIgnoreCase(s.getString("at", "CASTER"));
        String t = s.getString("trigger");
        this.trigger = t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    public static SoundSpec parse(ConfigurationSection s) {
        if (s == null || s.getString("key") == null) return null;
        return new SoundSpec(s);
    }

    /** Chuẩn hóa khoá âm thanh: ENTITY_BLAZE_SHOOT → entity.blaze.shoot; khoá có dấu chấm giữ nguyên. */
    public static String soundKey(String s) {
        if (s.contains(".") || s.contains(":")) return s.toLowerCase(Locale.ROOT);
        return s.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    /** progress 0..1 của giai đoạn, dùng để nội suy pitch. */
    public void play(Location l, double progress) {
        if (l == null || l.getWorld() == null) return;
        float p = Float.isNaN(pitchEnd) ? pitch : (float) (pitch + (pitchEnd - pitch) * progress);
        l.getWorld().playSound(l, soundKey(key), SoundCategory.MASTER, volume, Math.max(0.5f, Math.min(2.0f, p)));
    }

    public int delay() { return delay; }
    public int interval() { return interval; }
    public boolean atTarget() { return atTarget; }
    public String trigger() { return trigger; }
}
