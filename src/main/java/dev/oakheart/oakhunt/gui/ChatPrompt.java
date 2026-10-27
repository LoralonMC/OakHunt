package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One-line chat input for menus (same pattern as OakTags). Typing "cancel", clicking [Cancel] or
 * waiting out the timeout hands the callback null. The chat message is cancelled so it never
 * reaches chat. When the value being edited already has text, [Paste current] puts it in the
 * chat box to edit.
 */
public class ChatPrompt implements Listener {

    private static final int TIMEOUT_SECONDS = 60;

    private final OakHunt plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatPrompt(OakHunt plugin) {
        this.plugin = plugin;
    }

    /** Longest text the client's chat box takes, so longer values get no paste button. */
    private static final int CHAT_LIMIT = 256;

    private record Session(Consumer<String> callback, BukkitTask timeout) {
    }

    /**
     * Closes the player's menu, shows the prompt and waits for their next chat line.
     *
     * @param promptKey messages.yml key of the prompt text
     */
    public void ask(Player player, String promptKey, Consumer<String> callback) {
        ask(player, promptKey, null, callback);
    }

    /**
     * @param current the value being edited, offered as [Paste current]; null or blank for none
     */
    public void ask(Player player, String promptKey, String current, Consumer<String> callback) {
        cancel(player.getUniqueId());
        player.closeInventory();
        plugin.getMessages().cmd(player, promptKey);
        UUID uuid = player.getUniqueId();
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session session = sessions.remove(uuid);
            if (session != null) session.callback().accept(null);
        }, TIMEOUT_SECONDS * 20L);
        sessions.put(uuid, new Session(callback, timeout));
        player.sendMessage(actions(current));
    }

    private Component actions(String current) {
        Component paste = Component.empty();
        if (current != null && !current.isBlank() && current.length() <= CHAT_LIMIT) {
            paste = plugin.getMessages().line("commands.prompt-paste")
                    .hoverEvent(HoverEvent.showText(Component.text(current)))
                    .clickEvent(ClickEvent.suggestCommand(current));
        }
        // A real command rather than a click callback: Pl-Hide only lets allowlisted commands through.
        Component cancel = plugin.getMessages().line("commands.prompt-cancel")
                .clickEvent(ClickEvent.runCommand("/headhunt cancelprompt"));
        return plugin.getMessages().line("commands.prompt-actions",
                Placeholder.component("paste", paste), Placeholder.component("cancel", cancel));
    }

    /** Ends the player's prompt as if they typed "cancel". */
    public void abort(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.timeout().cancel();
        session.callback().accept(null);
    }

    public void cancel(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null) session.timeout().cancel();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        session.timeout().cancel();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String value = text.equalsIgnoreCase("cancel") ? null : text;
        Bukkit.getScheduler().runTask(plugin, () -> session.callback().accept(value));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}
