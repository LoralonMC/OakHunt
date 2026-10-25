package dev.oakheart.oakhunt.util;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oakhunt.model.Hunt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Thin facade over OakheartLib's MessageManager with the plugin's common placeholders.
 */
public class Messages {

    private final MessageManager manager;

    public Messages(MessageManager manager) {
        this.manager = manager;
    }

    public MessageManager manager() {
        return manager;
    }

    /** Gameplay message ({@code <key>.text} + {@code <key>.display}). */
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        manager.send(sender, key, resolvers);
    }

    /** Command message ({@code commands.<key>}), always chat. */
    public void cmd(CommandSender sender, String key, TagResolver... resolvers) {
        manager.sendCommand(sender, key, resolvers);
    }

    /**
     * Command message whose click actions carry values. MiniMessage doesn't resolve placeholders
     * inside tag arguments, so {@code {name}} tokens are replaced in the raw text first.
     */
    public void cmd(CommandSender sender, String key, Map<String, String> tokens, TagResolver... resolvers) {
        String text = manager.getConfig().getString("commands." + key, "");
        if (text.isEmpty()) return;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            text = text.replace("{" + token.getKey() + "}", token.getValue());
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(text, resolvers));
    }

    /** A single-line component without inherited italic, e.g. an item name. Empty if disabled. */
    public Component line(String key, TagResolver... resolvers) {
        String text = manager.getMessageText(key);
        return text == null ? Component.empty() : manager.deserialize(text, resolvers);
    }

    /** Lore lines for a key holding a list (or one string). */
    public List<Component> lines(String key, TagResolver... resolvers) {
        return manager.parseLines(key, resolvers);
    }

    /** Raw MiniMessage (e.g. a hunt's display name) without inherited italic. */
    public Component raw(String miniMessage, TagResolver... resolvers) {
        return manager.deserialize(miniMessage == null ? "" : miniMessage, resolvers);
    }

    public boolean isEnabled(String key) {
        String text = manager.getMessageText(key);
        return text != null && !text.isEmpty();
    }

    public static TagResolver hunt(Hunt hunt) {
        return Placeholder.parsed("hunt", hunt.getDisplayName());
    }

    public static TagResolver huntId(Hunt hunt) {
        return Placeholder.unparsed("hunt_id", hunt.getId());
    }

    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver mini(String name, String value) {
        return Placeholder.parsed(name, value == null ? "" : value);
    }
}
