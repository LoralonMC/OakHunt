package dev.oakheart.oakhunt.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Skin;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Resolves skin sources (HeadDatabase id, player name or UUID, texture value or hash) and builds
 * placer heads. Placers carry their hunt and skin in persistent data and are never used up.
 */
public class SkinService {

    /** Skin id stored on a placer that picks a random skin from the set each time. */
    public static final int RANDOM = -1;

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final OakHunt plugin;
    private final NamespacedKey placerHuntKey;
    private final NamespacedKey placerSkinKey;

    public SkinService(OakHunt plugin) {
        this.plugin = plugin;
        this.placerHuntKey = new NamespacedKey(plugin, "placer-hunt");
        this.placerSkinKey = new NamespacedKey(plugin, "placer-skin");
    }

    /** A resolved skin: the texture value and a short label for menus. */
    public record Resolved(String texture, String label) {
    }

    /**
     * @return the resolved skin, or null if the source matched nothing. Completes on the main thread.
     */
    public CompletableFuture<Resolved> resolve(String source) {
        String input = source.trim();
        String hdbId = input.toLowerCase().startsWith("hdb:") ? input.substring(4) : null;
        if (hdbId == null && DIGITS.matcher(input).matches()) hdbId = input;
        if (hdbId != null) {
            String texture = SkinUtil.textureOf(plugin.getHeadDatabaseHook().head(hdbId));
            return CompletableFuture.completedFuture(texture == null ? null : new Resolved(texture, "hdb:" + hdbId));
        }
        String texture = SkinUtil.normalizeTexture(input);
        if (texture != null) return CompletableFuture.completedFuture(new Resolved(texture, "texture"));

        PlayerProfile profile;
        try {
            profile = Bukkit.createProfile(UUID.fromString(input));
        } catch (IllegalArgumentException notUuid) {
            if (!PLAYER_NAME.matcher(input).matches()) return CompletableFuture.completedFuture(null);
            profile = Bukkit.createProfile(input);
        }
        CompletableFuture<Resolved> result = new CompletableFuture<>();
        profile.update().whenComplete((updated, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String value = error == null ? SkinUtil.textureOf(updated) : null;
            result.complete(value == null ? null : new Resolved(value, updated.getName() != null ? updated.getName() : input));
        }));
        return result;
    }

    /**
     * @param skin the skin to place, or null for a random pick from the set each time
     */
    public ItemStack placer(Hunt hunt, Skin skin) {
        List<Skin> set = plugin.getHuntManager().skins(hunt);
        String shown = skin != null ? skin.getTexture() : set.getFirst().getTexture();
        String mode = skin != null ? "#" + skin.getId() : "random";
        ItemStack item = ItemBuilder.of(SkinUtil.headItem(shown))
                .name(plugin.getMessages().line("placer-name", Messages.hunt(hunt)))
                .lore(plugin.getMessages().lines("placer-lore", Messages.hunt(hunt), Messages.text("skin", mode),
                        Messages.text("count", set.size())))
                .glint(skin == null)
                .build();
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(placerHuntKey, PersistentDataType.STRING, hunt.getId());
            pdc.set(placerSkinKey, PersistentDataType.INTEGER, skin != null ? skin.getId() : RANDOM);
        });
        return item;
    }

    /** The hunt id a placer belongs to, or null if the item isn't a placer. */
    public String placerHunt(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(placerHuntKey, PersistentDataType.STRING);
    }

    /**
     * The texture a placer puts down: its fixed skin, or a random one from the set.
     *
     * @return null if the skin is gone or the set is empty
     */
    public String placerTexture(Hunt hunt, ItemStack item) {
        Integer skinId = item.getItemMeta().getPersistentDataContainer().get(placerSkinKey, PersistentDataType.INTEGER);
        List<Skin> set = plugin.getHuntManager().skins(hunt);
        if (skinId == null || skinId == RANDOM) {
            return set.isEmpty() ? null : set.get(ThreadLocalRandom.current().nextInt(set.size())).getTexture();
        }
        return plugin.getHuntManager().skin(skinId).map(Skin::getTexture).orElse(null);
    }
}
