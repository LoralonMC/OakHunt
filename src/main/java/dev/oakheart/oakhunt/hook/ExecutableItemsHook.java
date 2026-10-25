package dev.oakheart.oakhunt.hook;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds ExecutableItems stacks by id, by reflection (same approach as OakSmith: SCore ships inside
 * the premium ExecutableItems download, so it can't be a build dependency).
 */
public class ExecutableItemsHook {

    private static final String API_CLASS = "com.ssomar.score.api.executableitems.ExecutableItemsAPI";

    private final Logger logger;
    private Method getManager;
    private Method getExecutableItem;
    private Method buildItem;
    private boolean available;

    public ExecutableItemsHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            if (!(boolean) api.getMethod("isEnabled").invoke(null)) return;
            getManager = api.getMethod("getExecutableItemsManager");
            getExecutableItem = getManager.getReturnType().getMethod("getExecutableItem", String.class);
            Class<?> itemType = Class.forName("com.ssomar.score.api.executableitems.config.ExecutableItemInterface");
            buildItem = itemType.getMethod("buildItem", int.class, Optional.class);
            available = true;
            logger.info("ExecutableItems integration enabled");
        } catch (ClassNotFoundException e) {
            logger.info("ExecutableItems not found; ExecutableItems rewards are unavailable");
        } catch (ReflectiveOperationException | ClassCastException e) {
            logger.log(Level.WARNING, "Could not reach the ExecutableItems API", e);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * @return the built item, or null if the id is unknown or ExecutableItems is unavailable
     */
    public ItemStack build(String id, int amount, Player player) {
        if (!available || id == null || id.isBlank()) return null;
        try {
            Object result = getExecutableItem.invoke(getManager.invoke(null), id);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            Object built = buildItem.invoke(optional.get(), amount, Optional.ofNullable(player));
            return built instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to build ExecutableItem '" + id + "'", e);
            return null;
        }
    }
}
