package me.astrax.ultimate;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Sự kiện: khóa di chuyển khi Charge/Stun, gậy skill, gián đoạn khi bị đánh, dọn dẹp khi thoát/chết. */
public final class UltimateListener implements Listener {

    private final AstraXUltimate plugin;

    public UltimateListener(AstraXUltimate plugin) {
        this.plugin = plugin;
    }

    /** Giữ nguyên vị trí (vẫn cho xoay đầu) khi bị khóa hoặc bị choáng. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        boolean frozen = plugin.skills().isLocked(p.getUniqueId()) || plugin.statuses().isStunned(p.getUniqueId());
        if (!frozen) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location keep = from.clone();
            keep.setYaw(to.getYaw());
            keep.setPitch(to.getPitch());
            e.setTo(keep);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        SkillDef def = plugin.skills().boundSkill(e.getItem());
        if (def == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        CommandManager.report(plugin, p, plugin.skills().cast(p, def, false, 1.0));
    }

    /** Bị đánh trong lúc Cast/Charge: gián đoạn nếu skill cấu hình interrupt.on-damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        SkillRun run = plugin.skills().activeRun(p.getUniqueId());
        if (run != null && run.def().interruptOnDamage() && run.interruptible() && e.getFinalDamage() > 0) {
            run.cancel("bị đánh");
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        plugin.statuses().clear(e.getEntity().getUniqueId());
        if (e.getEntity() instanceof Player p) plugin.skills().interrupt(p.getUniqueId(), "tử vong");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.skills().onQuit(e.getPlayer());
    }
}
