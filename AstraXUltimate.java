package me.astrax.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** AstraXUltimate - hệ thống Skill RPG cao cấp (Cast → Charge → Impact → After) cho Paper 1.21.x, Java 21. */
public final class AstraXUltimate extends JavaPlugin {

    private ConfigManager configs;
    private QualityManager quality;
    private StatusManager statuses;
    private CombatEngine combat;
    private CooldownManager cooldowns;
    private PlayerDataStore data;
    private SkillManager skills;

    private long tick;
    private boolean ready;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        configs.load();
        quality = new QualityManager(this);
        quality.load();
        statuses = new StatusManager(this);
        statuses.load();
        combat = new CombatEngine(this);
        cooldowns = new CooldownManager(this);
        data = new PlayerDataStore(this);
        data.load();
        skills = new SkillManager(this);
        skills.load();

        CommandManager commands = new CommandManager(this);
        PluginCommand cmd = getCommand("ultimate");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(new UltimateListener(this), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tick++;
            quality.tick();
        }, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            statuses.tick();
            cooldowns.tick();
        }, 2L, 2L);

        ready = true;
        getLogger().info("AstraXUltimate đã sẵn sàng (" + skills.all().size() + " skill, chất lượng "
                + quality.quality() + ").");
    }

    @Override
    public void onDisable() {
        if (!ready) return;
        skills.shutdown();
        statuses.shutdown();
        combat.shutdown();
        cooldowns.shutdown();
        data.save();
    }

    /** /ult reload: nạp lại cấu hình & skill. Skill đang chạy giữ nguyên định nghĩa cũ đến khi kết thúc. */
    public void reloadAll() {
        configs.load();
        quality.load();
        statuses.load();
        skills.load();
    }

    /** Bộ đếm tick của plugin (dùng cho trạng thái/hạn thời gian). */
    public long now() {
        return tick;
    }

    public ConfigManager configs() { return configs; }
    public QualityManager quality() { return quality; }
    public StatusManager statuses() { return statuses; }
    public CombatEngine combat() { return combat; }
    public CooldownManager cooldowns() { return cooldowns; }
    public PlayerDataStore data() { return data; }
    public SkillManager skills() { return skills; }
}
