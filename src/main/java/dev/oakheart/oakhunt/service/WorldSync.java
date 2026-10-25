package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Keeps the world matching each hunt's state. A head is a real block exactly when its hunt is
 * active and the head is live; everything else is removed. Unloaded chunks catch up when they load,
 * so nothing is ever force-loaded except by an explicit start check.
 *
 * <p>Placed blocks carry their head id in the skull's persistent data, which is how the plugin
 * tells its own heads from lookalikes (and never deletes a block that took a head's spot).
 */
public class WorldSync {

    private final OakHunt plugin;
    private final NamespacedKey headKey;

    public WorldSync(OakHunt plugin) {
        this.plugin = plugin;
        this.headKey = new NamespacedKey(plugin, "head");
    }

    /** A conflict found by the start check: the head and what occupies its spot. */
    public record Conflict(Head head, Material occupant) {
    }

    public void applyHunt(Hunt hunt) {
        for (Head head : plugin.getHuntManager().heads(hunt)) {
            reconcile(head);
        }
    }

    public void reconcileChunk(Chunk chunk) {
        for (Head head : plugin.getHuntManager().headsInChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())) {
            if (!head.isArchived()) reconcile(head);
        }
    }

    /** Brings one head's block in line with its hunt's state, if its chunk is loaded. */
    public void reconcile(Head head) {
        Block block = blockIfLoaded(head);
        if (block == null) return;
        Hunt hunt = plugin.getHuntManager().hunt(head.getHuntId()).orElse(null);
        boolean wanted = hunt != null && hunt.isActive() && head.isLive();
        boolean ours = isOurs(block, head);
        if (wanted) {
            if (ours) return;
            if (plugin.getConfigManager().isReplaceable(block.getType())) {
                place(block, head);
            } else {
                markConflict(hunt, head, block.getType());
            }
        } else if (ours) {
            clear(block);
        }
    }

    public void removeFromWorld(Head head) {
        Block block = blockIfLoaded(head);
        if (block != null && isOurs(block, head)) clear(block);
    }

    /** Re-applies a head's skin if its block is in the world. */
    public void refreshSkin(Head head) {
        Block block = blockIfLoaded(head);
        if (block != null && isOurs(block, head) && block.getState() instanceof Skull skull) {
            SkinUtil.apply(skull, head.getTexture());
            skull.update(true, false);
        }
    }

    /** Clears a head's conflict and puts it down, replacing whatever is there. */
    public void forcePlace(Head head, String actor) {
        head.setConflict(false);
        plugin.getHuntManager().saveHead(head);
        plugin.getDatabase().audit(actor, head.getHuntId(), "force-place", "#" + head.getNumber());
        Block block = blockIfLoaded(head);
        if (block == null) return;
        Hunt hunt = plugin.getHuntManager().hunt(head.getHuntId()).orElse(null);
        if (hunt != null && hunt.isActive() && !isOurs(block, head)) {
            if (!block.getType().isAir() && plugin.getConfigManager().isCoreProtectEnabled()) {
                plugin.getCoreProtectHook().logRemoval(block.getLocation(), block.getBlockData());
            }
            place(block, head);
        }
        plugin.getEditorService().refreshHead(head);
    }

    /**
     * Checks every live head's spot, loading chunks as needed.
     *
     * @return conflicts, completed on the main thread
     */
    public CompletableFuture<List<Conflict>> check(Hunt hunt) {
        List<Head> heads = plugin.getHuntManager().heads(hunt);
        List<CompletableFuture<Conflict>> checks = new ArrayList<>();
        for (Head head : heads) {
            World world = Bukkit.getWorld(head.getWorld());
            if (world == null) {
                checks.add(CompletableFuture.completedFuture(new Conflict(head, Material.BARRIER)));
                continue;
            }
            checks.add(world.getChunkAtAsync(head.getX() >> 4, head.getZ() >> 4).thenApply(chunk -> {
                Block block = world.getBlockAt(head.getX(), head.getY(), head.getZ());
                if (isOurs(block, head) || plugin.getConfigManager().isReplaceable(block.getType())) return null;
                return new Conflict(head, block.getType());
            }));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(v -> checks.stream().map(CompletableFuture::join).filter(c -> c != null).toList());
    }

    public boolean isOurs(Block block, Head head) {
        Integer id = headIdAt(block);
        return id != null && id == head.getId();
    }

    /** The OakHunt head id stored in a skull block, or null. */
    public Integer headIdAt(Block block) {
        Material type = block.getType();
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) return null;
        BlockState state = block.getState(false);
        if (!(state instanceof Skull skull)) return null;
        return skull.getPersistentDataContainer().get(headKey, PersistentDataType.INTEGER);
    }

    private void place(Block block, Head head) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(head.getBlockData());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Head #" + head.getNumber() + " of " + head.getHuntId()
                    + " has invalid block data; using a plain head", e);
            data = Bukkit.createBlockData(Material.PLAYER_HEAD);
        }
        block.setBlockData(data, false);
        if (block.getState() instanceof Skull skull) {
            SkinUtil.apply(skull, head.getTexture());
            skull.getPersistentDataContainer().set(headKey, PersistentDataType.INTEGER, head.getId());
            skull.update(true, false);
        }
        if (plugin.getConfigManager().isCoreProtectEnabled()) {
            plugin.getCoreProtectHook().logPlacement(block.getLocation(), data);
        }
    }

    private void clear(Block block) {
        BlockData old = block.getBlockData();
        block.setType(Material.AIR, false);
        if (plugin.getConfigManager().isCoreProtectEnabled()) {
            plugin.getCoreProtectHook().logRemoval(block.getLocation(), old);
        }
    }

    private void markConflict(Hunt hunt, Head head, Material occupant) {
        if (head.isConflict()) return;
        head.setConflict(true);
        plugin.getHuntManager().saveHead(head);
        plugin.getDatabase().audit("#world", hunt.getId(), "conflict", "#" + head.getNumber() + " " + occupant);
        plugin.getLogger().warning("Head #" + head.getNumber() + " of hunt " + hunt.getId() + " at "
                + head.getWorld() + " " + head.getX() + "," + head.getY() + "," + head.getZ()
                + " is blocked by " + occupant + "; skipped until resolved");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("oakhunt.edit")) sendConflict(player, hunt, head, occupant);
        }
        plugin.getEditorService().refreshHead(head);
    }

    public void sendConflict(Player player, Hunt hunt, Head head, Material occupant) {
        plugin.getMessages().cmd(player, "conflict-line", java.util.Map.of("head_id", String.valueOf(head.getId())),
                Messages.hunt(hunt),
                Messages.text("number", head.getNumber()), Messages.text("head_id", head.getId()),
                Messages.text("x", head.getX()), Messages.text("y", head.getY()), Messages.text("z", head.getZ()),
                Messages.text("block", occupant.getKey().getKey()));
    }

    private Block blockIfLoaded(Head head) {
        World world = Bukkit.getWorld(head.getWorld());
        if (world == null || !world.isChunkLoaded(head.getX() >> 4, head.getZ() >> 4)) return null;
        return world.getBlockAt(head.getX(), head.getY(), head.getZ());
    }

    public Location location(Head head) {
        World world = Bukkit.getWorld(head.getWorld());
        return world == null ? null : new Location(world, head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
    }
}
