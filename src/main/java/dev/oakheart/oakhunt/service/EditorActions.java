package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.entity.Player;

/**
 * What an editor's click on one of their hunt's heads does, for real blocks and fake previews alike:
 * sneak + left-click removes, right-click opens the head's menu.
 */
public class EditorActions {

    private final OakHunt plugin;

    public EditorActions(OakHunt plugin) {
        this.plugin = plugin;
    }

    public void click(Player player, Head head, boolean left) {
        Hunt hunt = plugin.getHuntManager().hunt(head.getHuntId()).orElse(null);
        if (hunt == null || head.isArchived()) return;
        if (plugin.getEditorService().isHider(player)) {
            hiderClick(player, hunt, head, left);
            return;
        }
        if (left && player.isSneaking()) {
            remove(player, hunt, head);
        } else if (left) {
            plugin.getMessages().send(player, "edit-remove-hint");
            plugin.getEditorService().resendFake(player, head);
        } else {
            plugin.getEditorService().resendFake(player, head);
            plugin.getMenus().openHead(player, hunt, head);
        }
    }

    /** Hiders see every head but may only remove their own, and get info instead of the head menu. */
    private void hiderClick(Player player, Hunt hunt, Head head, boolean left) {
        boolean own = player.getName().equalsIgnoreCase(head.getCreatedBy());
        if (left && player.isSneaking() && own) {
            remove(player, hunt, head);
            return;
        }
        plugin.getEditorService().resendFake(player, head);
        if (left && player.isSneaking()) {
            plugin.getMessages().send(player, "hider-not-yours", Messages.text("number", head.getNumber()),
                    Messages.text("placed_by", head.getCreatedBy() == null ? "-" : head.getCreatedBy()));
        } else if (left) {
            plugin.getMessages().send(player, "edit-remove-hint");
        } else {
            plugin.getMessages().send(player, "hider-head-info", Messages.text("number", head.getNumber()),
                    Messages.text("placed_by", head.getCreatedBy() == null ? "-" : head.getCreatedBy()));
        }
    }

    public void remove(Player player, Hunt hunt, Head head) {
        plugin.getHuntManager().archiveHead(head, player.getName());
        plugin.getEditorService().pushUndo(player, head, false);
        plugin.getMessages().send(player, "head-removed", Messages.hunt(hunt), Messages.text("number", head.getNumber()));
    }
}
