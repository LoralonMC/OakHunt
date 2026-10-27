package dev.oakheart.oakhunt.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Grants OakTags chat tags and builds their vouchers, by reflection, so OakTags stays optional.
 * Only tags with the "granted" unlock type can be rewards: permission tags can't be handed out.
 */
public class OakTagsHook {

    /** A grantable tag: its id and MiniMessage display. */
    public record TagChoice(String id, String display) {
    }

    private final Logger logger;
    private Method getInstance;
    private Method getTag;
    private Method getAllTags;
    private Method grantTag;
    private Constructor<?> voucherBuilder;
    private Method buildVoucher;

    public OakTagsHook(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        Plugin oakTags = Bukkit.getPluginManager().getPlugin("OakTags");
        if (oakTags == null || !oakTags.isEnabled()) {
            logger.info("OakTags not found; chat tag rewards are unavailable");
            return;
        }
        try {
            ClassLoader loader = oakTags.getClass().getClassLoader();
            Class<?> api = Class.forName("dev.oakheart.oaktags.api.OakTagsAPI", true, loader);
            getInstance = api.getMethod("getInstance");
            getTag = api.getMethod("getTag", String.class);
            getAllTags = api.getMethod("getAllTags");
            grantTag = api.getMethod("grantTag", UUID.class, String.class);
            Class<?> builder = Class.forName("dev.oakheart.oaktags.api.TagVoucherBuilder", true, loader);
            voucherBuilder = builder.getConstructor(String.class);
            buildVoucher = builder.getMethod("build");
            logger.info("OakTags integration enabled");
        } catch (ReflectiveOperationException e) {
            getInstance = null;
            logger.log(Level.WARNING, "Could not reach the OakTags API", e);
        }
    }

    public boolean isAvailable() {
        return getInstance != null;
    }

    /** Tags that can be granted, by id. */
    public List<TagChoice> grantableTags() {
        List<TagChoice> tags = new ArrayList<>();
        Object api = api();
        if (api == null) return tags;
        try {
            for (Object info : (Collection<?>) getAllTags.invoke(api)) {
                TagChoice choice = grantable(info);
                if (choice != null) tags.add(choice);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            logger.log(Level.WARNING, "Failed to list OakTags tags", e);
        }
        tags.sort(Comparator.comparing(TagChoice::id));
        return tags;
    }

    public boolean isGrantable(String id) {
        Object api = api();
        if (api == null || id == null || id.isBlank()) return false;
        try {
            return grantable(getTag.invoke(api, id)) != null;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to look up OakTags tag '" + id + "'", e);
            return false;
        }
    }

    /**
     * @return true if the player didn't own the tag and now does
     */
    public boolean grant(Player player, String id) {
        Object api = api();
        if (api == null) return false;
        try {
            return (boolean) grantTag.invoke(api, player.getUniqueId(), id);
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to grant OakTags tag '" + id + "' to " + player.getName(), e);
            return false;
        }
    }

    /**
     * @return the voucher, or null if the tag is unknown or OakTags is unavailable
     */
    public ItemStack voucher(String id, int amount) {
        if (!isAvailable() || id == null || id.isBlank()) return null;
        try {
            Object built = buildVoucher.invoke(voucherBuilder.newInstance(id));
            if (!(built instanceof ItemStack stack)) return null;
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (ReflectiveOperationException e) {
            logger.log(Level.WARNING, "Failed to build an OakTags voucher for '" + id + "'", e);
            return null;
        }
    }

    private Object api() {
        if (!isAvailable()) return null;
        try {
            return getInstance.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** The tag as a choice if it's a "granted" tag, else null. TagInfo is a record. */
    private static TagChoice grantable(Object info) throws ReflectiveOperationException {
        if (info == null) return null;
        Class<?> type = info.getClass();
        Object unlock = type.getMethod("unlockType").invoke(info);
        if (unlock == null || !"GRANTED".equals(((Enum<?>) unlock).name())) return null;
        return new TagChoice(String.valueOf(type.getMethod("id").invoke(info)),
                String.valueOf(type.getMethod("display").invoke(info)));
    }
}
