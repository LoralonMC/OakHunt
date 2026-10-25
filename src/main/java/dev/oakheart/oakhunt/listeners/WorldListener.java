package dev.oakheart.oakhunt.listeners;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.util.Messages;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Chunk catch-up for the world sync, editor fake-block resends, and join checks.
 */
public class WorldListener implements Listener {

    private final OakHunt plugin;

    public WorldListener(OakHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // A tick later: changing blocks inside the load event itself is asking for trouble.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) plugin.getWorldSync().reconcileChunk(chunk);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        plugin.getEditorService().onChunkSent(event.getPlayer(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Hunt hunt : plugin.getHuntManager().activeHunts()) {
                plugin.getHuntManager().currentRun(hunt)
                        .ifPresent(run -> plugin.getRewardService().checkMilestones(player, hunt, run));
                if (!player.hasPermission("oakhunt.edit")) continue;
                long conflicts = plugin.getHuntManager().heads(hunt).stream().filter(Head::isConflict).count();
                if (conflicts > 0) {
                    plugin.getMessages().cmd(player, "conflicts-on-join", java.util.Map.of("hunt_id", hunt.getId()), Messages.hunt(hunt),
                            Messages.huntId(hunt), Messages.text("count", conflicts));
                }
            }
        }, 60L);
    }
}
