package dev.oakheart.oakhunt.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds Nexo items by id, by reflection, so Nexo stays optional.
 */
public class NexoHook {

    private final Logger logger;
    private Method itemFromId;
    private Method build;

    public NexoHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            logger.info("Nexo not found; Nexo rewards are unavailable");
            return;
        }
        try {
            Class<?> items = Class.forName("com.nexomc.nexo.api.NexoItems");
            itemFromId = items.getMethod("itemFromId", String.class);
            build = itemFromId.getReturnType().getMethod("build");
            logger.info("Nexo integration enabled");
        } catch (ReflectiveOperationException e) {
            itemFromId = null;
            logger.log(Level.WARNING, "Could not reach the Nexo API", e);
        }
    }

    public boolean isAvailable() {
        return itemFromId != null && build != null;
    }

    /**
     * @return the built item, or null if the id is unknown or Nexo is unavailable
     */
    public ItemStack build(String id, int amount) {
        if (!isAvailable() || id == null || id.isBlank()) return null;
        try {
            Object builder = itemFromId.invoke(null, id);
            if (builder == null) return null;
            Object built = build.invoke(builder);
            if (!(built instanceof ItemStack stack)) return null;
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to build Nexo item '" + id + "'", e);
            return null;
        }
    }
}
