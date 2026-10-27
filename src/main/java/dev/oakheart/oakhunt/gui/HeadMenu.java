package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/**
 * One head: change its skin, set its clue, force-place it if blocked, remove it.
 */
public class HeadMenu extends Menu {

    private final Hunt hunt;
    private final Head head;

    public HeadMenu(OakHunt plugin, Player viewer, Hunt hunt, Head head) {
        super(plugin, viewer, 3, "gui.head-title", Messages.text("number", head.getNumber()));
        this.hunt = hunt;
        this.head = head;
    }

    @Override
    protected void draw() {
        int finds = 0;
        Run run = plugin.getHuntManager().lastRun(hunt).orElse(null);
        if (run != null) {
            try {
                finds = plugin.getDatabase().loadFindCounts(run.getId()).getOrDefault(head.getId(), 0);
            } catch (SQLException ignored) {
                // count stays 0; the menu is informational
            }
        }
        set(4, ItemBuilder.of(SkinUtil.headItem(head.getTexture()))
                .name(plugin.getMessages().line("gui.head-icon-name", Messages.text("number", head.getNumber())))
                .lore(plugin.getMessages().lines(head.isConflict() ? "gui.head-icon-conflict-lore" : "gui.head-icon-lore",
                        HeadsMenu.details(head, finds)))
                .build());
        set(11, button(Material.PLAYER_HEAD, "head-skin"), e -> {
            String texture = SkinUtil.textureOf(viewer.getInventory().getItemInMainHand());
            if (texture == null) {
                plugin.getMessages().cmd(viewer, "hold-a-head");
                return;
            }
            plugin.getHuntManager().addSkin(hunt, texture, "placed", viewer.getName());
            plugin.getHuntManager().setHeadTexture(head, texture, viewer.getName());
            plugin.getMessages().cmd(viewer, "head-skin-changed", Messages.text("number", head.getNumber()));
            redraw();
        });
        set(13, button(Material.WRITABLE_BOOK, "head-clue", Messages.text("clue", head.getClue() == null ? "-" : head.getClue())),
                e -> plugin.getChatPrompt().ask(viewer, "prompt-clue", head.getClue(), input -> {
                    if (input != null) {
                        plugin.getHuntManager().setClue(head, input.equalsIgnoreCase("clear") ? null : input, viewer.getName());
                        plugin.getMessages().cmd(viewer, "clue-set", Messages.text("number", head.getNumber()));
                    }
                    plugin.getMenus().openHead(viewer, hunt, head);
                }));
        if (head.isConflict()) {
            set(15, button(Material.ANVIL, "force-place"), e -> {
                if (!viewer.hasPermission("oakhunt.manage")) {
                    plugin.getMessages().cmd(viewer, "no-permission");
                    return;
                }
                plugin.getWorldSync().forcePlace(head, viewer.getName());
                plugin.getMessages().cmd(viewer, "force-placed", Messages.text("number", head.getNumber()));
                redraw();
            });
        }
        set(22, button(Material.BARRIER, "remove-head"), e -> plugin.getMenus().choose(viewer, "confirm-remove-head", () -> {
            plugin.getEditorActions().remove(viewer, hunt, head);
        }, () -> plugin.getMenus().openHead(viewer, hunt, head)));
        back(18, () -> plugin.getMenus().openHeads(viewer, hunt, 0));
    }
}
