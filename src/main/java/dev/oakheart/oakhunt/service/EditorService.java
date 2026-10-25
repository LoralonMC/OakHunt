package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Edit mode. Each editor sees their hunt's heads, numbered: real blocks while the hunt is active,
 * otherwise per-player fake blocks that exist only on their client. Labels are hidden,
 * non-persistent text displays shown only to that editor, so nothing is left behind on restart.
 *
 * <p>Clicks on fake blocks never reach the server as block events; {@code PacketClickListener}
 * catches them and looks them up through {@link #previewAt}, which is safe off the main thread.
 */
public class EditorService {

    private static final int UNDO_LIMIT = 50;
    private static final long NEARBY_RESEND_MS = 3000;
    private static final double NEARBY_RESEND_RADIUS = 12;

    private final OakHunt plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> previewIndex = new ConcurrentHashMap<>();
    private BukkitTask task;

    public EditorService(OakHunt plugin) {
        this.plugin = plugin;
    }

    private static final class Session {
        private final String huntId;
        private String world;
        private final Map<Integer, TextDisplay> labels = new HashMap<>();
        private final Map<Integer, String> labelTexts = new HashMap<>();
        private final Set<Integer> fakes = new HashSet<>();
        private final Deque<Undo> undo = new ArrayDeque<>();
        private long lastNearbyResend;

        private Session(String huntId) {
            this.huntId = huntId;
        }
    }

    /** One undoable edit: a head that was added, or one that was removed. */
    private record Undo(int headId, boolean added) {
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID uuid : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) exit(player);
        }
        sessions.clear();
        previewIndex.clear();
    }

    // ---------------------------------------------------------------- sessions

    public void enter(Player player, Hunt hunt) {
        exit(player);
        Session session = new Session(hunt.getId());
        sessions.put(player.getUniqueId(), session);
        render(player, session);
    }

    public void exit(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        previewIndex.remove(player.getUniqueId());
        if (session == null) return;
        session.labels.values().forEach(label -> {
            if (label.isValid()) label.remove();
        });
        if (player.isOnline() && player.getWorld().getName().equals(session.world)) {
            for (int id : session.fakes) {
                plugin.getHuntManager().headIncludingArchived(id).ifPresent(head -> sendReal(player, head));
            }
        }
    }

    public Optional<Hunt> editing(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? Optional.empty() : plugin.getHuntManager().hunt(session.huntId);
    }

    public boolean isEditing(Player player, String huntId) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.huntId.equals(huntId);
    }

    /** Ends every session on a hunt (it was archived). */
    public void closeSessions(Hunt hunt) {
        for (UUID uuid : List.copyOf(sessions.keySet())) {
            Session session = sessions.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (session != null && session.huntId.equals(hunt.getId()) && player != null) {
                exit(player);
                plugin.getMessages().send(player, "edit-closed", Messages.hunt(hunt));
            }
        }
    }

    // ---------------------------------------------------------------- undo

    public void pushUndo(Player player, Head head, boolean added) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.undo.push(new Undo(head.getId(), added));
        while (session.undo.size() > UNDO_LIMIT) session.undo.removeLast();
    }

    /**
     * @return the head the undo affected, or empty if there was nothing to undo
     */
    public Optional<Head> undo(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.undo.isEmpty()) return Optional.empty();
        Undo undo = session.undo.pop();
        Head head = plugin.getHuntManager().headIncludingArchived(undo.headId()).orElse(null);
        if (head == null) return Optional.empty();
        if (undo.added()) {
            plugin.getHuntManager().archiveHead(head, player.getName());
        } else if (!plugin.getHuntManager().restoreHead(head, player.getName())) {
            return Optional.empty();
        }
        return Optional.of(head);
    }

    // ---------------------------------------------------------------- refresh hooks

    public void refreshHunt(Hunt hunt) {
        forSessions(hunt.getId(), this::render);
    }

    public void refreshHead(Head head) {
        forSessions(head.getHuntId(), (player, session) -> {
            if (head.isArchived()) {
                removeLabel(session, head.getId());
                if (session.fakes.remove(head.getId())) {
                    unindex(player, head);
                    sendReal(player, head);
                }
            } else {
                session.labelTexts.remove(head.getId());
                session.fakes.remove(head.getId());
            }
            render(player, session);
        });
    }

    /** Re-sends a fake block the client may have dropped (e.g. after a click). */
    public void resendFake(Player player, Head head) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.fakes.remove(head.getId());
        render(player, session);
    }

    /** A chunk was (re)sent to the player, wiping any fake blocks in it: queue them to be re-sent. */
    public void onChunkSent(Player player, int chunkX, int chunkZ) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        boolean any = false;
        for (int id : List.copyOf(session.fakes)) {
            Head head = plugin.getHuntManager().headIncludingArchived(id).orElse(null);
            if (head != null && head.getX() >> 4 == chunkX && head.getZ() >> 4 == chunkZ) {
                session.fakes.remove(id);
                any = true;
            }
        }
        if (!any) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session && player.isOnline()) render(player, session);
        });
    }

    /** Head id previewed for a player at a position, or null. Safe off the main thread. */
    public Integer previewAt(UUID player, int x, int y, int z) {
        Map<Long, Integer> index = previewIndex.get(player);
        return index == null ? null : index.get(pack(x, y, z));
    }

    private void forSessions(String huntId, java.util.function.BiConsumer<Player, Session> action) {
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            if (!entry.getValue().huntId.equals(huntId)) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) action.accept(player, entry.getValue());
        }
    }

    // ---------------------------------------------------------------- rendering

    private void tick() {
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                sessions.remove(entry.getKey());
                previewIndex.remove(entry.getKey());
                continue;
            }
            render(player, entry.getValue());
        }
    }

    private void render(Player player, Session session) {
        Hunt hunt = plugin.getHuntManager().hunt(session.huntId).orElse(null);
        if (hunt == null) {
            exit(player);
            return;
        }
        String world = player.getWorld().getName();
        if (!world.equals(session.world)) {
            session.labels.values().forEach(label -> {
                if (label.isValid()) label.remove();
            });
            session.labels.clear();
            session.labelTexts.clear();
            session.fakes.clear();
            previewIndex.remove(player.getUniqueId());
            session.world = world;
        }
        double radius = plugin.getConfigManager().getPreviewRadius();
        double radiusSquared = radius * radius;
        Location at = player.getLocation();
        Set<Integer> seen = new HashSet<>();
        for (Head head : plugin.getHuntManager().heads(hunt)) {
            if (!head.getWorld().equals(world)) continue;
            double dx = head.getX() + 0.5 - at.getX();
            double dy = head.getY() + 0.5 - at.getY();
            double dz = head.getZ() + 0.5 - at.getZ();
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
            seen.add(head.getId());
            label(player, session, head);
            if (hunt.isActive()) {
                if (session.fakes.remove(head.getId())) unindex(player, head);
            } else {
                showFake(player, session, head);
            }
        }
        for (int id : List.copyOf(session.labels.keySet())) {
            if (!seen.contains(id)) removeLabel(session, id);
        }
        for (int id : List.copyOf(session.fakes)) {
            if (seen.contains(id)) continue;
            session.fakes.remove(id);
            plugin.getHuntManager().headIncludingArchived(id).ifPresent(head -> {
                unindex(player, head);
                sendReal(player, head);
            });
        }
        if (!hunt.isActive()) refreshNearbyFakes(player, session);
        plugin.getMessages().send(player, "edit-actionbar", Messages.hunt(hunt),
                Messages.text("count", plugin.getHuntManager().heads(hunt).size()),
                Messages.text("state", plugin.getHuntManager().state(hunt).key()));
    }

    private void showFake(Player player, Session session, Head head) {
        Block block = player.getWorld().getBlockAt(head.getX(), head.getY(), head.getZ());
        if (!plugin.getConfigManager().isReplaceable(block.getType())) {
            if (session.fakes.remove(head.getId())) unindex(player, head);
            return;
        }
        if (session.fakes.contains(head.getId())) return;
        sendFake(player, block.getLocation(), head);
        session.fakes.add(head.getId());
        previewIndex.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(pack(head.getX(), head.getY(), head.getZ()), head.getId());
    }

    private void sendFake(Player player, Location location, Head head) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(head.getBlockData());
        } catch (IllegalArgumentException e) {
            data = Bukkit.createBlockData(Material.PLAYER_HEAD);
        }
        player.sendBlockChange(location, data);
        BlockState state = data.createBlockState();
        if (state instanceof Skull skull) {
            SkinUtil.apply(skull, head.getTexture());
            player.sendBlockUpdate(location, skull);
        }
    }

    /**
     * Re-sends fake heads close to the editor every few seconds. A client can drop a fake block
     * whenever the server sends it that position (a cancelled placement's revert, a neighbour
     * update), and the editor is most likely to be looking at the ones nearby.
     */
    private void refreshNearbyFakes(Player player, Session session) {
        long now = System.currentTimeMillis();
        if (now - session.lastNearbyResend < NEARBY_RESEND_MS) return;
        session.lastNearbyResend = now;
        Location at = player.getLocation();
        for (int id : session.fakes) {
            Head head = plugin.getHuntManager().head(id).orElse(null);
            if (head == null) continue;
            double dx = head.getX() + 0.5 - at.getX();
            double dy = head.getY() + 0.5 - at.getY();
            double dz = head.getZ() + 0.5 - at.getZ();
            if (dx * dx + dy * dy + dz * dz > NEARBY_RESEND_RADIUS * NEARBY_RESEND_RADIUS) continue;
            Block block = player.getWorld().getBlockAt(head.getX(), head.getY(), head.getZ());
            if (plugin.getConfigManager().isReplaceable(block.getType())) sendFake(player, block.getLocation(), head);
        }
    }

    /** Re-sends a just-placed head's preview a few ticks later, after the placement revert lands. */
    public void resendSoon(Head head) {
        for (long delay : new long[]{2L, 10L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> forSessions(head.getHuntId(), (player, session) -> {
                if (!session.fakes.contains(head.getId())) return;
                Block block = player.getWorld().getBlockAt(head.getX(), head.getY(), head.getZ());
                if (plugin.getConfigManager().isReplaceable(block.getType())) sendFake(player, block.getLocation(), head);
            }), delay);
        }
    }

    private void sendReal(Player player, Head head) {
        World world = Bukkit.getWorld(head.getWorld());
        if (world == null || !player.getWorld().equals(world)) return;
        Block block = world.getBlockAt(head.getX(), head.getY(), head.getZ());
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private void unindex(Player player, Head head) {
        Map<Long, Integer> index = previewIndex.get(player.getUniqueId());
        if (index != null) index.remove(pack(head.getX(), head.getY(), head.getZ()));
    }

    private void label(Player player, Session session, Head head) {
        String text = labelText(head);
        TextDisplay existing = session.labels.get(head.getId());
        if (existing != null && existing.isValid()) {
            if (!text.equals(session.labelTexts.get(head.getId()))) {
                existing.text(labelComponent(head));
                session.labelTexts.put(head.getId(), text);
            }
            return;
        }
        World world = player.getWorld();
        if (!world.isChunkLoaded(head.getX() >> 4, head.getZ() >> 4)) return;
        Location location = new Location(world, head.getX() + 0.5,
                head.getY() + plugin.getConfigManager().getLabelHeight(), head.getZ() + 0.5);
        try {
            TextDisplay display = world.spawn(location, TextDisplay.class, td -> {
                td.setVisibleByDefault(false);
                td.setPersistent(false);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0x60, 0, 0, 0));
                td.text(labelComponent(head));
            });
            player.showEntity(plugin, display);
            session.labels.put(head.getId(), display);
            session.labelTexts.put(head.getId(), text);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Could not spawn editor label", e);
        }
    }

    private void removeLabel(Session session, int headId) {
        TextDisplay display = session.labels.remove(headId);
        session.labelTexts.remove(headId);
        if (display != null && display.isValid()) display.remove();
    }

    /** Cache key for a label's content, so unchanged labels aren't re-sent. */
    private static String labelText(Head head) {
        return head.getNumber() + "|" + (head.getClue() != null) + "|" + head.isConflict();
    }

    private Component labelComponent(Head head) {
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.getMessages().line("editor-label", Messages.text("number", head.getNumber())));
        if (head.getClue() != null) lines.add(plugin.getMessages().line("editor-label-clue"));
        if (head.isConflict()) lines.add(plugin.getMessages().line("editor-label-conflict"));
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
