package me.astrax.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Nạp config.yml / skills.yml / messages.yml và tiện ích tin nhắn. */
public final class ConfigManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final AstraXUltimate plugin;
    private FileConfiguration skills;
    private FileConfiguration messages;

    public ConfigManager(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        skills = loadYaml("skills.yml", false);
        messages = loadYaml("messages.yml", true);
    }

    private FileConfiguration loadYaml(String name, boolean withDefaults) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) plugin.saveResource(name, false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (withDefaults) {
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            } catch (IOException ignored) {
                // dùng file trên đĩa
            }
        }
        return y;
    }

    public FileConfiguration skills() { return skills; }
    public FileConfiguration messages() { return messages; }

    // ---------------- Tin nhắn ----------------
    public static String replace(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s);
    }

    public String raw(String key, String... kv) {
        String s = messages.getString(key);
        if (s == null) s = "&cThiếu thông báo: " + key;
        return replace(s, kv);
    }

    public Component comp(String key, String... kv) {
        return color(raw("prefix") + raw(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(comp(key, kv));
    }

    public void sendRaw(CommandSender to, String key, String... kv) {
        to.sendMessage(color(raw(key, kv)));
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : messages.getStringList(key)) to.sendMessage(color(replace(line, kv)));
    }

    public void actionBar(Player p, String key, String... kv) {
        p.sendActionBar(color(raw(key, kv)));
    }
}
