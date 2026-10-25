package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Skin;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A hunt's skin set. Click a skin for its placer, right-click to replace it with the held head,
 * shift-click to remove it from the set.
 */
public class SkinsMenu extends Menu {

    private static final int PER_PAGE = 36;

    private final Hunt hunt;
    private final int page;

    public SkinsMenu(OakHunt plugin, Player viewer, Hunt hunt, int page) {
        super(plugin, viewer, 5, "gui.skins-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
        this.page = page;
    }

    @Override
    protected void draw() {
        List<Skin> skins = plugin.getHuntManager().skins(hunt);
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= skins.size()) break;
            Skin skin = skins.get(index);
            long used = plugin.getHuntManager().heads(hunt).stream().filter(h -> h.getTexture().equals(skin.getTexture())).count();
            set(i, ItemBuilder.of(SkinUtil.headItem(skin.getTexture()))
                    .name(plugin.getMessages().line("gui.skin-icon-name", Messages.text("id", skin.getId())))
                    .lore(plugin.getMessages().lines("gui.skin-icon-lore", Messages.text("label", skin.getLabel() == null ? "-" : skin.getLabel()),
                            Messages.text("used", used)))
                    .build(), e -> {
                if (!edit()) return;
                if (e.isShiftClick()) {
                    plugin.getHuntManager().removeSkin(skin, viewer.getName());
                    plugin.getMessages().cmd(viewer, "skin-removed", Messages.text("id", skin.getId()));
                    redraw();
                } else if (e.isRightClick()) {
                    String texture = SkinUtil.textureOf(viewer.getInventory().getItemInMainHand());
                    if (texture == null) {
                        plugin.getMessages().cmd(viewer, "hold-a-head");
                        return;
                    }
                    plugin.getMenus().choose(viewer, "replace-skin", () -> replace(skin, texture, true),
                            () -> replace(skin, texture, false));
                } else {
                    viewer.getInventory().addItem(plugin.getSkinService().placer(hunt, skin));
                    plugin.getMessages().cmd(viewer, "placer-given", Messages.hunt(hunt));
                }
            });
        }
        pager(page, pages(skins.size(), PER_PAGE), 39, 41, p -> plugin.getMenus().openSkins(viewer, hunt, p));
        back(36, () -> plugin.getMenus().openHunt(viewer, hunt));
        set(38, button(Material.PLAYER_HEAD, "add-held-skin"), e -> {
            if (!edit()) return;
            String texture = SkinUtil.textureOf(viewer.getInventory().getItemInMainHand());
            if (texture == null) {
                plugin.getMessages().cmd(viewer, "hold-a-head");
                return;
            }
            plugin.getHuntManager().addSkin(hunt, texture, "held", viewer.getName());
            plugin.getMessages().cmd(viewer, "skin-added", Messages.hunt(hunt));
            redraw();
        });
        set(40, button(Material.NAME_TAG, "add-source-skin"), e -> {
            if (!edit()) return;
            plugin.getChatPrompt().ask(viewer, "prompt-skin-source", input -> {
                if (input == null) {
                    plugin.getMenus().openSkins(viewer, hunt, page);
                    return;
                }
                plugin.getSkinService().resolve(input).thenAccept(resolved -> {
                    if (resolved == null) {
                        plugin.getMessages().cmd(viewer, "skin-not-found", Messages.text("input", input));
                    } else {
                        plugin.getHuntManager().addSkin(hunt, resolved.texture(), resolved.label(), viewer.getName());
                        plugin.getMessages().cmd(viewer, "skin-added", Messages.hunt(hunt));
                    }
                    plugin.getMenus().openSkins(viewer, hunt, page);
                });
            });
        });
        if (!skins.isEmpty()) {
            set(42, button(Material.ENDER_EYE, "random-placer"), e -> {
                if (!edit()) return;
                viewer.getInventory().addItem(plugin.getSkinService().placer(hunt, null));
                plugin.getMessages().cmd(viewer, "placer-given", Messages.hunt(hunt));
            });
        }
    }

    private void replace(Skin skin, String texture, boolean updatePlaced) {
        int updated = plugin.getHuntManager().replaceSkin(skin, texture, updatePlaced, viewer.getName());
        plugin.getMessages().cmd(viewer, "skin-replaced", Messages.text("count", updated));
        plugin.getMenus().openSkins(viewer, hunt, page);
    }

    private boolean edit() {
        if (viewer.hasPermission("oakhunt.edit")) return true;
        plugin.getMessages().cmd(viewer, "no-permission");
        return false;
    }
}
