package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * A hunt's heads: click to teleport, right-click for the head's menu, shift-click to remove.
 */
public class HeadsMenu extends Menu {

    private static final int[] SLOTS = new int[36];

    static {
        for (int i = 0; i < 36; i++) SLOTS[i] = i;
    }

    private final Hunt hunt;
    private final int page;

    public HeadsMenu(OakHunt plugin, Player viewer, Hunt hunt, int page) {
        super(plugin, viewer, 5, "gui.heads-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
        this.page = page;
    }

    @Override
    protected void draw() {
        List<Head> heads = plugin.getHuntManager().heads(hunt);
        Map<Integer, Integer> counts = findCounts();
        for (int i = 0; i < SLOTS.length; i++) {
            int index = page * SLOTS.length + i;
            if (index >= heads.size()) break;
            Head head = heads.get(index);
            String key = head.isConflict() ? "gui.head-icon-conflict-lore" : "gui.head-icon-lore";
            set(SLOTS[i], ItemBuilder.of(SkinUtil.headItem(head.getTexture()))
                    .name(plugin.getMessages().line("gui.head-icon-name", Messages.text("number", head.getNumber())))
                    .lore(plugin.getMessages().lines(key, details(head, counts.getOrDefault(head.getId(), 0))))
                    .build(), e -> {
                if (!viewer.hasPermission("oakhunt.edit")) {
                    plugin.getMessages().cmd(viewer, "no-permission");
                    return;
                }
                if (e.isShiftClick()) {
                    plugin.getMenus().choose(viewer, "confirm-remove-head", () -> {
                        plugin.getHuntManager().archiveHead(head, viewer.getName());
                        plugin.getMessages().cmd(viewer, "head-removed-gui", Messages.hunt(hunt), Messages.text("number", head.getNumber()));
                        plugin.getMenus().openHeads(viewer, hunt, page);
                    }, () -> plugin.getMenus().openHeads(viewer, hunt, page));
                } else if (e.isRightClick()) {
                    plugin.getMenus().openHead(viewer, hunt, head);
                } else {
                    Location location = plugin.getWorldSync().location(head);
                    if (location == null) return;
                    viewer.closeInventory();
                    viewer.teleportAsync(location.add(0, 1, 0));
                }
            });
        }
        pager(page, pages(heads.size(), SLOTS.length), 39, 41, p -> plugin.getMenus().openHeads(viewer, hunt, p));
        back(36, () -> plugin.getMenus().openHunt(viewer, hunt));
    }

    private Map<Integer, Integer> findCounts() {
        Run run = plugin.getHuntManager().lastRun(hunt).orElse(null);
        if (run == null) return Map.of();
        try {
            return plugin.getDatabase().loadFindCounts(run.getId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load find counts", e);
            return Map.of();
        }
    }

    static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] details(Head head, int finds) {
        return new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Messages.text("number", head.getNumber()), Messages.text("world", head.getWorld()),
                Messages.text("x", head.getX()), Messages.text("y", head.getY()), Messages.text("z", head.getZ()),
                Messages.text("clue", head.getClue() == null ? "-" : head.getClue()),
                Messages.text("finds", finds)};
    }
}
