package me.astrax.ultimate;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lưu dạng tiến hóa của skill theo từng người chơi (players.yml). Thay đổi hiếm nên ghi đồng bộ. */
public final class PlayerDataStore {

    private final AstraXUltimate plugin;
    private final File file;
    private final Map<UUID, Set<String>> evolved = new HashMap<>();

    public PlayerDataStore(AstraXUltimate plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        evolved.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Set<String> set = new HashSet<>(players.getStringList(key + ".evolved"));
                if (!set.isEmpty()) evolved.put(id, set);
            } catch (IllegalArgumentException ignored) {
                // bỏ qua khóa lỗi
            }
        }
    }

    public boolean hasEvolved(UUID id, String skillId) {
        Set<String> s = evolved.get(id);
        return s != null && s.contains(skillId);
    }

    public void setEvolved(UUID id, String skillId, boolean value) {
        Set<String> s = evolved.computeIfAbsent(id, k -> new HashSet<>());
        if (value) s.add(skillId);
        else s.remove(skillId);
        if (s.isEmpty()) evolved.remove(id);
        save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : evolved.entrySet()) {
            y.set("players." + e.getKey() + ".evolved", new java.util.ArrayList<>(e.getValue()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Không thể lưu players.yml: " + ex.getMessage());
        }
    }
}
