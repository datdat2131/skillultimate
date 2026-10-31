package me.astrax.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Đăng ký skill, kiểm tra điều kiện thi triển, quản lý skill đang chạy, khóa di chuyển, gắn skill vào vật phẩm. */
public final class SkillManager {

    public enum CastResult { OK, UNKNOWN, NO_PERMISSION, COOLDOWN, BUSY, SILENCED, STUNNED, NO_TARGET, WORLD_DISABLED, LIMIT }

    public enum EvolveResult { OK, UNKNOWN, NO_EVOLUTION, ALREADY, NOT_ENOUGH }

    private static final Set<String> TYPES = Set.of("METEOR_FALL", "THUNDER_JUDGEMENT", "VOID_BREAKER",
            "DRAGON_DESCENT", "DEATH_DOMAIN");

    private final AstraXUltimate plugin;
    private final NamespacedKey boundKey;
    private final Map<String, SkillDef> defs = new LinkedHashMap<>();
    private final Map<UUID, SkillRun> runs = new HashMap<>();
    private final Set<UUID> locked = new HashSet<>();

    public SkillManager(AstraXUltimate plugin) {
        this.plugin = plugin;
        this.boundKey = new NamespacedKey(plugin, "skill");
    }

    // ---------------- Nạp skill ----------------
    public void load() {
        defs.clear();
        ConfigurationSection root = plugin.configs().skills().getConfigurationSection("skills");
        if (root == null) {
            plugin.getLogger().warning("skills.yml không có mục 'skills'.");
            return;
        }
        for (String id : root.getKeys(false)) {
            try {
                ConfigurationSection merged = resolve(root, id, 0);
                if (merged == null) continue;
                SkillDef d = new SkillDef(id.toLowerCase(Locale.ROOT), merged, plugin.getLogger());
                if (!TYPES.contains(d.type())) {
                    plugin.getLogger().warning("Skill '" + id + "': type không hợp lệ (" + d.type() + ")");
                    continue;
                }
                if (!d.hasPhase(PhaseDef.Id.IMPACT)) {
                    plugin.getLogger().warning("Skill '" + id + "': thiếu giai đoạn impact.");
                    continue;
                }
                defs.put(d.id(), d);
            } catch (Exception e) {
                plugin.getLogger().warning("Không thể nạp skill '" + id + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Đã nạp " + defs.size() + " skill.");
    }

    /** Hỗ trợ "extends": skill con kế thừa toàn bộ skill cha rồi ghi đè các khóa riêng (gộp sâu). */
    private ConfigurationSection resolve(ConfigurationSection root, String id, int depth) {
        ConfigurationSection s = root.getConfigurationSection(id);
        if (s == null || depth > 5) return s;
        String parent = s.getString("extends");
        if (parent == null) return s;
        ConfigurationSection base = resolve(root, parent, depth + 1);
        if (base == null) return s;
        MemoryConfiguration out = new MemoryConfiguration();
        copy(base, out);
        copy(s, out);
        return out;
    }

    private void copy(ConfigurationSection src, ConfigurationSection dst) {
        for (String k : src.getKeys(true)) {
            if (src.isConfigurationSection(k)) continue;
            dst.set(k, src.get(k));
        }
    }

    public Collection<SkillDef> all() {
        return defs.values();
    }

    public SkillDef get(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    /** Dạng skill thực sự dùng cho người chơi (đã tiến hóa hay chưa). */
    public SkillDef effective(UUID uuid, SkillDef base) {
        if (base.evolution() != null && plugin.data().hasEvolved(uuid, base.id())) {
            SkillDef evo = defs.get(base.evolution().toLowerCase(Locale.ROOT));
            if (evo != null) return evo;
        }
        return base;
    }

    // ---------------- Thi triển ----------------
    public CastResult cast(Player p, String id, boolean force) {
        SkillDef base = get(id);
        return base == null ? CastResult.UNKNOWN : cast(p, base, force, 1.0);
    }

    /** power: hệ số sức mạnh (API cho plugin khác, ví dụ nhân theo level). */
    public CastResult cast(Player p, SkillDef base, boolean force, double power) {
        UUID uuid = p.getUniqueId();
        if (!force && plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName())) {
            return CastResult.WORLD_DISABLED;
        }
        if (!force && !p.hasPermission(base.permission())) return CastResult.NO_PERMISSION;
        if (runs.containsKey(uuid)) return CastResult.BUSY;
        if (!force) {
            if (plugin.statuses().isSilenced(uuid)) return CastResult.SILENCED;
            if (plugin.statuses().isStunned(uuid)) return CastResult.STUNNED;
        }
        boolean bypass = force || p.hasPermission("astraxultimate.bypass");
        SkillDef def = effective(uuid, base);
        if (!bypass && plugin.cooldowns().remaining(uuid, base.id()) > 0) {
            plugin.cooldowns().showCooling(p, base.id(), def.displayName());
            return CastResult.COOLDOWN;
        }
        if (runs.size() >= plugin.getConfig().getInt("limits.max-concurrent-runs", 30)) return CastResult.LIMIT;

        Location target;
        LivingEntity te = null;
        switch (def.targetMode()) {
            case "SELF" -> target = p.getLocation().clone();
            case "ENTITY" -> {
                te = TargetUtil.aimEntity(p, def.range());
                target = te != null ? te.getLocation() : TargetUtil.aimGround(p, def.range(), def.allowMiss());
            }
            default -> target = TargetUtil.aimGround(p, def.range(), def.allowMiss());
        }
        if (target == null) return CastResult.NO_TARGET;

        SkillRun run = createRun(def, base.id(), p, target, te, power);
        if (run == null) return CastResult.UNKNOWN;
        runs.put(uuid, run);
        if (!bypass) plugin.cooldowns().start(p, base.id(), def.displayName(), def.cooldown());
        run.start();
        return CastResult.OK;
    }

    private SkillRun createRun(SkillDef def, String baseId, Player p, Location target, LivingEntity te, double power) {
        return switch (def.type()) {
            case "METEOR_FALL" -> new MeteorFallRun(plugin, def, baseId, p, target, te, power);
            case "THUNDER_JUDGEMENT" -> new ThunderJudgementRun(plugin, def, baseId, p, target, te, power);
            case "VOID_BREAKER" -> new VoidBreakerRun(plugin, def, baseId, p, target, te, power);
            case "DRAGON_DESCENT" -> new DragonDescentRun(plugin, def, baseId, p, target, te, power);
            case "DEATH_DOMAIN" -> new DeathDomainRun(plugin, def, baseId, p, target, te, power);
            default -> null;
        };
    }

    // ---------------- Skill đang chạy ----------------
    public SkillRun activeRun(UUID uuid) {
        return runs.get(uuid);
    }

    /** Gián đoạn skill (chỉ khi còn ở giai đoạn CAST/CHARGE). */
    public void interrupt(UUID uuid, String reason) {
        SkillRun run = runs.get(uuid);
        if (run != null && run.interruptible()) run.cancel(reason);
    }

    public boolean cancel(Player p) {
        SkillRun run = runs.get(p.getUniqueId());
        if (run == null) return false;
        run.cancel("hủy");
        return true;
    }

    /** SkillRun gọi khi kết thúc (bình thường hoặc bị hủy). */
    public void onRunEnded(SkillRun run, boolean cancelled, String reason) {
        runs.remove(run.casterId(), run);
        if (!cancelled) return;
        Player p = run.caster();
        if (p.isOnline() && !reason.equals("shutdown") && !reason.equals("offline")) {
            plugin.configs().send(p, "interrupted", "reason", reason);
        }
        if (!run.reachedImpact()) {
            plugin.cooldowns().shorten(run.casterId(), run.baseId(),
                    plugin.getConfig().getDouble("cooldown.cancel-penalty-seconds", 5));
        }
    }

    public int activeCount() {
        return runs.size();
    }

    // ---------------- Khóa di chuyển ----------------
    public void lock(UUID id) { locked.add(id); }
    public void unlock(UUID id) { locked.remove(id); }
    public boolean isLocked(UUID id) { return locked.contains(id); }

    public void onQuit(Player p) {
        SkillRun run = runs.get(p.getUniqueId());
        if (run != null) run.cancel("offline");
        locked.remove(p.getUniqueId());
        plugin.statuses().clear(p.getUniqueId());
        plugin.cooldowns().onQuit(p);
    }

    public void shutdown() {
        for (SkillRun run : new ArrayList<>(runs.values())) run.cancel("shutdown");
        runs.clear();
        locked.clear();
    }

    // ---------------- Gắn skill vào vật phẩm ----------------
    public boolean bind(Player p, SkillDef def) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() == Material.AIR) return false;
        tag(it, def);
        p.getInventory().setItemInMainHand(it);
        return true;
    }

    public boolean unbind(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() == Material.AIR || boundSkill(it) == null) return false;
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().remove(boundKey);
        it.setItemMeta(meta);
        p.getInventory().setItemInMainHand(it);
        return true;
    }

    private void tag(ItemStack it, SkillDef def) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(boundKey, PersistentDataType.STRING, def.id());
        List<Component> lore = new ArrayList<>();
        for (String l : plugin.getConfig().getStringList("wand.lore")) {
            lore.add(ConfigManager.color(ConfigManager.replace(l, "id", def.id(), "skill", def.displayName()))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        if (!lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
    }

    /** Skill gắn trên vật phẩm, null nếu không có. */
    public SkillDef boundSkill(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(boundKey, PersistentDataType.STRING);
        return id == null ? null : defs.get(id);
    }

    public ItemStack createWand(SkillDef def) {
        ConfigurationSection w = plugin.getConfig().getConfigurationSection("wand");
        if (w == null) w = new MemoryConfiguration();
        Material m = Material.matchMaterial(w.getString("material", "BLAZE_ROD"));
        ItemStack it = new ItemStack(m == null ? Material.BLAZE_ROD : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(ConfigManager.color(ConfigManager.replace(w.getString("name", "{skill}"),
                "skill", def.displayName(), "id", def.id()))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (w.getBoolean("glow", true)) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        tag(it, def);
        return it;
    }

    // ---------------- Tiến hóa ----------------
    public EvolveResult evolve(Player p, String id, boolean free) {
        SkillDef base = get(id);
        if (base == null) return EvolveResult.UNKNOWN;
        if (base.evolution() == null || !defs.containsKey(base.evolution().toLowerCase(Locale.ROOT))) {
            return EvolveResult.NO_EVOLUTION;
        }
        if (plugin.data().hasEvolved(p.getUniqueId(), base.id())) return EvolveResult.ALREADY;
        if (!free) {
            int cost = base.evolveCost();
            if (p.getLevel() < cost) return EvolveResult.NOT_ENOUGH;
            p.setLevel(p.getLevel() - cost);
        }
        plugin.data().setEvolved(p.getUniqueId(), base.id(), true);
        return EvolveResult.OK;
    }

    public void devolve(UUID uuid, String id) {
        plugin.data().setEvolved(uuid, id.toLowerCase(Locale.ROOT), false);
    }
}
