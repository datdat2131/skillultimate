package me.astrax.ultimate;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Định nghĩa một skill đã phân tích từ skills.yml (bất biến). */
public final class SkillDef {

    private final String id;
    private final String type;
    private final String displayName;
    private final List<String> description;
    private final String permission;
    private final double cooldown;
    private final String targetMode;
    private final double range;
    private final boolean allowMiss;
    private final boolean trackTarget;
    private final boolean interruptOnDamage;
    private final double skyHeight;
    private final String evolution;
    private final int evolveCost;
    private final ConfigurationSection params;
    private final EnumMap<PhaseDef.Id, PhaseDef> phases = new EnumMap<>(PhaseDef.Id.class);

    public SkillDef(String id, ConfigurationSection s, Logger log) {
        this.id = id;
        this.type = s.getString("type", "").toUpperCase(Locale.ROOT);
        this.displayName = s.getString("display-name", id);
        this.description = s.getStringList("description");
        this.permission = s.getString("permission", "astraxultimate.skill." + id);
        this.cooldown = Math.max(0.0, s.getDouble("cooldown", 30));
        this.targetMode = s.getString("target.mode", "GROUND").toUpperCase(Locale.ROOT);
        this.range = s.getDouble("target.range", 40);
        this.allowMiss = s.getBoolean("target.allow-miss", true);
        this.trackTarget = s.getBoolean("target.track", false);
        this.interruptOnDamage = s.getBoolean("interrupt.on-damage", false);
        this.skyHeight = s.getDouble("sky-height", 20);
        String evo = s.getString("evolution");
        this.evolution = (evo == null || evo.isBlank() || evo.equalsIgnoreCase("none")) ? null : evo;
        this.evolveCost = Math.max(0, s.getInt("evolve-cost", 0));
        ConfigurationSection p = s.getConfigurationSection("params");
        this.params = p != null ? p : new MemoryConfiguration();
        for (PhaseDef.Id pid : PhaseDef.Id.values()) {
            ConfigurationSection ps = s.getConfigurationSection(pid.name().toLowerCase(Locale.ROOT));
            if (ps != null) phases.put(pid, new PhaseDef(pid, ps, log));
        }
    }

    /** Thứ tự các giai đoạn có định nghĩa. */
    public List<PhaseDef.Id> order() {
        List<PhaseDef.Id> o = new ArrayList<>();
        for (PhaseDef.Id pid : PhaseDef.Id.values()) if (phases.containsKey(pid)) o.add(pid);
        return o;
    }

    public PhaseDef phase(PhaseDef.Id pid) { return phases.get(pid); }
    public boolean hasPhase(PhaseDef.Id pid) { return phases.containsKey(pid); }

    public String id() { return id; }
    public String type() { return type; }
    public String displayName() { return displayName; }
    public List<String> description() { return Collections.unmodifiableList(description); }
    public String permission() { return permission; }
    public double cooldown() { return cooldown; }
    public String targetMode() { return targetMode; }
    public double range() { return range; }
    public boolean allowMiss() { return allowMiss; }
    public boolean trackTarget() { return trackTarget; }
    public boolean interruptOnDamage() { return interruptOnDamage; }
    public double skyHeight() { return skyHeight; }
    public String evolution() { return evolution; }
    public int evolveCost() { return evolveCost; }
    public ConfigurationSection params() { return params; }
}
