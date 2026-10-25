package dev.oakheart.oakhunt.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Looks up HeadDatabase heads by id, by reflection, so HeadDatabase stays optional.
 */
public class HeadDatabaseHook {

    private final Logger logger;
    private Object api;
    private Method getItemHead;

    public HeadDatabaseHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("HeadDatabase") == null) {
            logger.info("HeadDatabase not found; skins can't be added by HDB id");
            return;
        }
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            api = apiClass.getConstructor().newInstance();
            getItemHead = apiClass.getMethod("getItemHead", String.class);
            logger.info("HeadDatabase integration enabled");
        } catch (ReflectiveOperationException e) {
            api = null;
            logger.log(Level.WARNING, "Could not reach the HeadDatabase API", e);
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * @return the head item for an HDB id, or null if unknown. HDB loads its database
     *         asynchronously after startup, so early lookups can miss.
     */
    public ItemStack head(String id) {
        if (!isAvailable()) return null;
        try {
            Object item = getItemHead.invoke(api, id);
            return item instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.FINE, "HDB lookup failed for " + id, e);
            return null;
        }
    }
}
