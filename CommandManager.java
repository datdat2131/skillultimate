package me.astrax.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lệnh /ult (alias /ultimate) và tab completion. */
public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("list", "info", "cast", "bind", "unbind", "evolve", "cancel", "help");
    private static final List<String> ADMIN_SUBS = List.of("reload", "quality", "wand", "resetcd", "devolve");

    private final AstraXUltimate plugin;

    public CommandManager(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cm() {
        return plugin.configs();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            cm().sendList(sender, "help");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "cast" -> cast(sender, args);
            case "bind" -> bind(sender, args);
            case "unbind" -> unbind(sender);
            case "evolve" -> evolve(sender, args);
            case "cancel" -> cancel(sender);
            case "reload" -> reload(sender);
            case "quality" -> quality(sender, args);
            case "wand" -> wand(sender, args);
            case "resetcd" -> resetCd(sender, args);
            case "devolve" -> devolve(sender, args);
            default -> cm().sendList(sender, "help");
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        cm().send(s, "only-player");
        return null;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("astraxultimate.admin")) return true;
        cm().send(s, "no-permission");
        return false;
    }

    // ---------------- Người chơi ----------------
    private void list(CommandSender sender) {
        cm().sendRaw(sender, "list-header");
        for (SkillDef d : plugin.skills().all()) {
            if (d.id().equals("divine-thunder")) continue;       // dạng tiến hóa: ẩn khỏi danh sách
            String status;
            if (sender instanceof Player p) {
                SkillDef eff = plugin.skills().effective(p.getUniqueId(), d);
                double rem = plugin.cooldowns().remaining(p.getUniqueId(), d.id());
                if (!p.hasPermission(d.permission())) status = cm().raw("list-nopermission");
                else if (rem > 0) status = cm().raw("list-cooldown", "time", String.format(Locale.US, "%.1f", rem));
                else status = cm().raw("list-ready");
                cm().sendRaw(sender, "list-line", "name", eff.displayName(), "id", d.id(), "status", status);
            } else {
                cm().sendRaw(sender, "list-line", "name", d.displayName(), "id", d.id(), "status", "");
            }
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            cm().send(sender, "usage", "usage", "/ult info <skill>");
            return;
        }
        SkillDef d = plugin.skills().get(args[1]);
        if (d == null) {
            cm().send(sender, "unknown-skill", "skill", args[1]);
            return;
        }
        cm().sendList(sender, "info", "name", d.displayName(), "id", d.id(), "type", d.type(),
                "cooldown", String.valueOf(Math.round(d.cooldown())),
                "range", String.valueOf(Math.round(d.range())),
                "evolution", d.evolution() == null ? "-" : d.evolution());
        for (String line : d.description()) sender.sendMessage(ConfigManager.color(line));
    }

    private void cast(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            cm().send(p, "usage", "usage", "/ult cast <skill>");
            return;
        }
        SkillDef base = plugin.skills().get(args[1]);
        if (base == null) {
            cm().send(p, "unknown-skill", "skill", args[1]);
            return;
        }
        boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force") && p.hasPermission("astraxultimate.admin");
        report(p, plugin.skills().cast(p, base, force, 1.0));
    }

    /** Gửi thông báo tương ứng với kết quả thi triển. */
    public static void report(AstraXUltimate plugin, Player p, SkillManager.CastResult r) {
        ConfigManager cm = plugin.configs();
        switch (r) {
            case NO_PERMISSION -> cm.send(p, "no-permission");
            case BUSY -> cm.send(p, "busy");
            case SILENCED -> cm.send(p, "silenced");
            case STUNNED -> cm.send(p, "stunned");
            case NO_TARGET -> cm.send(p, "no-target");
            case WORLD_DISABLED -> cm.send(p, "world-disabled");
            case LIMIT -> cm.send(p, "limit-reached");
            case UNKNOWN -> cm.send(p, "unknown-skill", "skill", "?");
            default -> {
                // OK: không cần thông báo; COOLDOWN: đã hiện ActionBar
            }
        }
    }

    private void report(Player p, SkillManager.CastResult r) {
        report(plugin, p, r);
    }

    private void bind(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            cm().send(p, "usage", "usage", "/ult bind <skill>");
            return;
        }
        SkillDef d = plugin.skills().get(args[1]);
        if (d == null) {
            cm().send(p, "unknown-skill", "skill", args[1]);
            return;
        }
        if (!p.hasPermission(d.permission()) && !p.hasPermission("astraxultimate.bypass")) {
            cm().send(p, "no-permission");
            return;
        }
        if (plugin.skills().bind(p, d)) cm().send(p, "bound", "skill", d.displayName());
        else cm().send(p, "no-item");
    }

    private void unbind(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) {
            cm().send(p, "no-item");
            return;
        }
        if (plugin.skills().unbind(p)) cm().send(p, "unbound");
        else cm().send(p, "not-bound");
    }

    private void evolve(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            cm().send(p, "usage", "usage", "/ult evolve <skill>");
            return;
        }
        SkillDef base = plugin.skills().get(args[1]);
        if (base == null) {
            cm().send(p, "unknown-skill", "skill", args[1]);
            return;
        }
        boolean free = p.hasPermission("astraxultimate.bypass");
        switch (plugin.skills().evolve(p, base.id(), free)) {
            case OK -> {
                SkillDef evo = plugin.skills().get(base.evolution());
                cm().send(p, "evolve-ok", "from", base.displayName(), "to", evo == null ? "?" : evo.displayName());
                p.playSound(p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
            }
            case NO_EVOLUTION -> cm().send(p, "evolve-none");
            case ALREADY -> cm().send(p, "evolve-already");
            case NOT_ENOUGH -> cm().send(p, "evolve-cost", "cost", String.valueOf(base.evolveCost()),
                    "have", String.valueOf(p.getLevel()));
            case UNKNOWN -> cm().send(p, "unknown-skill", "skill", args[1]);
        }
    }

    private void cancel(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        if (plugin.skills().cancel(p)) cm().send(p, "cancelled");
        else cm().send(p, "nothing-to-cancel");
    }

    // ---------------- Quản trị ----------------
    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadAll();
        cm().send(sender, "reloaded", "count", String.valueOf(plugin.skills().all().size()));
    }

    private void quality(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            cm().send(sender, "usage", "usage", "/ult quality <LOW|MEDIUM|HIGH|ULTRA>");
            return;
        }
        QualityManager.Quality q = QualityManager.Quality.parse(args[1], null);
        if (q == null) {
            cm().send(sender, "invalid-quality");
            return;
        }
        plugin.quality().setQuality(q);
        cm().send(sender, "quality-set", "quality", q.name());
    }

    private void wand(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            cm().send(sender, "usage", "usage", "/ult wand <skill> [người]");
            return;
        }
        SkillDef d = plugin.skills().get(args[1]);
        if (d == null) {
            cm().send(sender, "unknown-skill", "skill", args[1]);
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            cm().send(sender, "player-offline");
            return;
        }
        Map<Integer, ItemStack> left = target.getInventory().addItem(plugin.skills().createWand(d));
        for (ItemStack rest : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), rest);
        cm().send(sender, "wand-given", "skill", d.displayName(), "player", target.getName());
    }

    private void resetCd(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
        if (t == null) {
            cm().send(sender, "player-offline");
            return;
        }
        plugin.cooldowns().reset(t.getUniqueId());
        cm().send(sender, "cooldowns-reset", "player", t.getName());
    }

    private void devolve(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            cm().send(sender, "usage", "usage", "/ult devolve <người> <skill>");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            cm().send(sender, "player-offline");
            return;
        }
        plugin.skills().devolve(t.getUniqueId(), args[2]);
        cm().send(sender, "devolved", "player", t.getName(), "skill", args[2]);
    }

    // ---------------- Tab completion ----------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("astraxultimate.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (isAdmin) subs.addAll(ADMIN_SUBS);
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "info", "cast", "bind", "evolve", "wand" -> {
                    return filter(skillIds(), args[1]);
                }
                case "quality" -> {
                    if (isAdmin) return filter(List.of("LOW", "MEDIUM", "HIGH", "ULTRA"), args[1]);
                }
                case "resetcd", "devolve" -> {
                    if (isAdmin) return filter(playerNames(), args[1]);
                }
                default -> {
                }
            }
        } else if (args.length == 3 && isAdmin) {
            if (sub.equals("wand")) return filter(playerNames(), args[2]);
            if (sub.equals("devolve")) return filter(skillIds(), args[2]);
            if (sub.equals("cast")) return filter(List.of("force"), args[2]);
        }
        return List.of();
    }

    private List<String> skillIds() {
        List<String> ids = new ArrayList<>();
        for (SkillDef d : plugin.skills().all()) ids.add(d.id());
        return ids;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
