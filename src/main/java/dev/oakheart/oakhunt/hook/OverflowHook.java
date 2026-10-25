package dev.oakheart.oakhunt.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hands reward items that don't fit the inventory to OakOverflow, reached by reflection.
 *
 * <p>Called directly rather than relying on OakOverflow's dropped-item catch, which skips items
 * when two players stand within its ambiguity threshold. That is the normal case at a busy hunt.
 */
public class OverflowHook {

    private final Logger logger;
    private Object api;
    private Method addOverflowItem;

    public OverflowHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        Plugin overflow = Bukkit.getPluginManager().getPlugin("OakOverflow");
        if (overflow == null || !overflow.isEnabled()) {
            logger.info("OakOverflow not found; reward items that don't fit will drop at the player's feet");
            return;
        }
        try {
            api = overflow.getClass().getMethod("getAPI").invoke(overflow);
            addOverflowItem = api.getClass().getMethod(
                    "addOverflowItem", Player.class, ItemStack.class, int.class, String.class);
            logger.info("OakOverflow integration enabled");
        } catch (ReflectiveOperationException e) {
            api = null;
            logger.log(Level.WARNING, "Failed to initialize OakOverflow integration", e);
        }
    }

    public boolean isAvailable() {
        return api != null && addOverflowItem != null;
    }

    /**
     * @return true if OakOverflow accepted the item
     */
    public boolean store(Player player, ItemStack item) {
        if (!isAvailable()) return false;
        try {
            CompletableFuture<?> future = (CompletableFuture<?>) addOverflowItem.invoke(
                    api, player, item, item.getAmount(), "OakHunt");
            future.exceptionally(throwable -> {
                logger.log(Level.SEVERE, "OakOverflow failed to store a hunt reward for " + player.getName()
                        + ": " + item.getType() + " x" + item.getAmount(), throwable);
                return null;
            });
            return true;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to invoke OakOverflow API", e);
            return false;
        }
    }
}
