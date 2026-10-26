package dev.oakheart.oakhunt.listeners;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Placing heads in edit mode. The vanilla placement is always cancelled and the plugin records the
 * head instead, so placers are never used up and inactive hunts never get a real block.
 */
public class EditorListener implements Listener {

    private final OakHunt plugin;

    public EditorListener(OakHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        String placerHunt = plugin.getSkinService().placerHunt(item);
        Hunt editing = plugin.getEditorService().editing(player).orElse(null);
        if (placerHunt != null && (editing == null || !editing.getId().equals(placerHunt))) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "placer-needs-edit", Messages.text("hunt_id", placerHunt));
            return;
        }
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        if (editing == null || (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD)) return;
        event.setCancelled(true);
        if (placerHunt == null && plugin.getEditorService().isHider(player)) {
            // Any other head would add its skin to the set, which hiders can't change.
            plugin.getMessages().send(player, "hider-placers-only", Messages.huntId(editing));
            return;
        }

        Head occupant = plugin.getHuntManager().headAt(block);
        if (occupant != null) {
            plugin.getHuntManager().hunt(occupant.getHuntId()).ifPresent(other ->
                    plugin.getMessages().send(player, "spot-taken", Messages.hunt(other)));
            return;
        }
        String texture = placerHunt != null
                ? plugin.getSkinService().placerTexture(editing, item)
                : SkinUtil.textureOf(item);
        if (texture == null) {
            plugin.getMessages().send(player, placerHunt != null ? "placer-no-skins" : "head-no-skin");
            return;
        }
        String blockData = block.getBlockData().getAsString();
        if (placerHunt == null) plugin.getHuntManager().addSkin(editing, texture, "placed", player.getName());
        // The cancelled placement reverts after this handler; record the head on the next tick so
        // an active hunt's real block isn't wiped by that revert.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getHuntManager().headAt(block) != null) return;
            Head head = plugin.getHuntManager().addHead(editing, block, blockData, texture, player.getName());
            plugin.getEditorService().pushUndo(player, head, true);
            plugin.getEditorService().resendSoon(head);
            plugin.getMessages().send(player, "head-added", Messages.hunt(editing),
                    Messages.text("number", head.getNumber()));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getEditorService().exit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getEditorService().editing(event.getPlayer())
                .ifPresent(hunt -> plugin.getEditorService().refreshHunt(hunt));
    }
}
