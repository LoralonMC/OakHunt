package dev.oakheart.oakhunt.hook;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.logging.Logger;

/**
 * Logs plugin head placements and removals to CoreProtect under the user {@code #oakhunt}.
 */
public class CoreProtectHook {

    private static final String USER = "#oakhunt";

    private final Logger logger;
    private CoreProtectAPI api;

    public CoreProtectHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        var plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (plugin == null || !plugin.isEnabled()) {
            logger.info("CoreProtect not found; head placements won't be logged");
            return;
        }
        // Only touch net.coreprotect types once the plugin is known to exist, or class
        // resolution fails on servers without it.
        CoreProtectAPI candidate = ((CoreProtect) plugin).getAPI();
        if (candidate.isEnabled() && candidate.APIVersion() >= 9) {
            api = candidate;
            logger.info("CoreProtect integration enabled");
        } else {
            logger.warning("CoreProtect API too old or disabled; head placements won't be logged");
        }
    }

    public void logPlacement(Location location, BlockData data) {
        if (api != null) api.logPlacement(USER, location, data.getMaterial(), data);
    }

    public void logRemoval(Location location, BlockData data) {
        if (api != null) api.logRemoval(USER, location, data.getMaterial(), data);
    }
}
