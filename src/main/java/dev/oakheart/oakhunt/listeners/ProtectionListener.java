package dev.oakheart.oakhunt.listeners;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Real hunt heads can't be broken, pushed, blown up or washed away. Removal goes through edit mode.
 */
public class ProtectionListener implements Listener {

    private final OakHunt plugin;

    public ProtectionListener(OakHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isHuntBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyHuntBlock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyHuntBlock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isHuntBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isHuntBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isHuntBlock(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (isHuntBlock(event.getBlock())) event.setCancelled(true);
    }

    /** Completion fireworks are for show. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        NamespacedKey key = plugin.getFeedbackService().getFireworkKey();
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private boolean anyHuntBlock(List<Block> blocks) {
        for (Block block : blocks) {
            if (isHuntBlock(block)) return true;
        }
        return false;
    }

    private boolean isHuntBlock(Block block) {
        return plugin.getHuntManager().placedHeadAt(block) != null;
    }
}
