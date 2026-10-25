package dev.oakheart.oakhunt.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Converts between skins (the base64 "textures" property value), profiles, head items and skulls.
 */
public final class SkinUtil {

    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{40,80}");
    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/";

    private SkinUtil() {}

    /** The texture value of a player head item, or null for a plain or non-head item. */
    public static String textureOf(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return null;
        return textureOf(meta.getPlayerProfile());
    }

    public static String textureOf(PlayerProfile profile) {
        if (profile == null) return null;
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) return property.getValue();
        }
        return null;
    }

    /**
     * Turns user input into a texture value: a base64 value passes through, and a texture hash
     * or textures.minecraft.net URL is wrapped into one.
     *
     * @return the texture value, or null if the input is neither
     */
    public static String normalizeTexture(String input) {
        if (input == null || input.isBlank()) return null;
        String text = input.trim();
        if (text.startsWith("http")) {
            int slash = text.lastIndexOf('/');
            text = slash >= 0 ? text.substring(slash + 1) : text;
        }
        if (HASH.matcher(text).matches()) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_URL + text.toLowerCase() + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
            return decoded.contains("textures") ? text : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A profile carrying only the texture. The id is derived from the texture so it is stable. */
    public static PlayerProfile profile(String texture) {
        UUID id = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(id, null);
        profile.setProperty(new ProfileProperty("textures", texture));
        return profile;
    }

    public static ItemStack headItem(String texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile(texture)));
        return item;
    }

    public static void apply(Skull skull, String texture) {
        skull.setProfile(ResolvableProfile.resolvableProfile(profile(texture)));
    }
}
