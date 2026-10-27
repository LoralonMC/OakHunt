package dev.oakheart.oakhunt.listeners;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.service.ProgressService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clicks on hunt heads: a find for players, inspect or remove for editors. Runs first and cancels,
 * so spawn protection never swallows a find.
 */
public class HeadListener implements Listener {

    private final OakHunt plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public HeadListener(OakHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        // An editor's own hunt comes first on a shared spot; otherwise it's whichever head stands here.
        Head head = plugin.getEditorService().editing(player)
                .map(editingHunt -> plugin.getHuntManager().headAt(block, editingHunt.getId()))
                .orElse(null);
        if (head == null) head = plugin.getHuntManager().placedHeadAt(block);
        if (head == null) return;
        Hunt hunt = plugin.getHuntManager().hunt(head.getHuntId()).orElse(null);
        if (hunt == null) return;
        boolean editing = plugin.getEditorService().isEditing(player, hunt.getId());
        // A conflicted spot holds someone else's block: leave it alone.
        if (!editing && !plugin.getWorldSync().isOurs(block, head)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!coolDown(player)) return;

        if (editing) {
            plugin.getEditorActions().click(player, head, action == Action.LEFT_CLICK_BLOCK);
            return;
        }
        if (!hunt.isActive() || !head.isLive() || !player.hasPermission("oakhunt.use")) return;
        Run run = plugin.getHuntManager().currentRun(hunt).orElse(null);
        if (run == null) return;

        ProgressService.FindResult result = plugin.getProgressService()
                .recordFind(player.getUniqueId(), player.getName(), hunt, run, head);
        Location at = block.getLocation().add(0.5, 0, 0.5);
        if (!result.isNew()) {
            plugin.getFeedbackService().alreadyFound(player, at, hunt, result);
            return;
        }
        plugin.getFeedbackService().newFind(player, at, hunt, result);
        plugin.getRewardService().checkMilestones(player, hunt, run);
        if (result.completedNow()) plugin.getFeedbackService().completed(player, hunt, result.rank());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    private boolean coolDown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < plugin.getConfigManager().getClickCooldownMs()) return false;
        lastClick.put(player.getUniqueId(), now);
        return true;
    }
}
