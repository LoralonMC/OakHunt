package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Skin;
import dev.oakheart.oakhunt.service.HuntManager;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Every hunt, with state and counts. Click one to manage it.
 */
public class HuntListMenu extends Menu {

    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final int page;

    public HuntListMenu(OakHunt plugin, Player viewer, int page) {
        super(plugin, viewer, 5, "gui.hunts-title");
        this.page = page;
    }

    @Override
    protected void draw() {
        List<Hunt> hunts = plugin.getHuntManager().hunts();
        int pages = pages(hunts.size(), SLOTS.length);
        for (int i = 0; i < SLOTS.length; i++) {
            int index = page * SLOTS.length + i;
            if (index >= hunts.size()) break;
            Hunt hunt = hunts.get(index);
            set(SLOTS[i], icon(plugin, hunt), e -> plugin.getMenus().openHunt(viewer, hunt));
        }
        pager(page, pages, 36, 44, p -> plugin.getMenus().openHuntList(viewer, p));
        set(40, button(Material.WRITABLE_BOOK, "create-hunt"), e -> plugin.getChatPrompt().ask(viewer, "prompt-hunt-id", input -> {
            if (input == null) return;
            String id = input.toLowerCase(Locale.ROOT);
            if (!HuntManager.ID_PATTERN.matcher(id).matches()) {
                plugin.getMessages().cmd(viewer, "invalid-hunt-id", Messages.text("hunt_id", input));
                return;
            }
            if (plugin.getHuntManager().huntIncludingArchived(id).isPresent()) {
                plugin.getMessages().cmd(viewer, "hunt-exists", Messages.text("hunt_id", id));
                return;
            }
            Hunt created = plugin.getHuntManager().createHunt(id, viewer.getName());
            plugin.getMessages().cmd(viewer, "hunt-created", java.util.Map.of("hunt_id", created.getId()), Messages.hunt(created), Messages.huntId(created));
            plugin.getMenus().openHunt(viewer, created);
        }));
    }

    /** The hunt's icon: its first skin, with state, counts and schedule in the lore. */
    static ItemStack icon(OakHunt plugin, Hunt hunt) {
        List<Skin> skins = plugin.getHuntManager().skins(hunt);
        ItemStack base = skins.isEmpty() ? new ItemStack(Material.PLAYER_HEAD) : SkinUtil.headItem(skins.getFirst().getTexture());
        return ItemBuilder.of(base)
                .name(plugin.getMessages().raw(hunt.getDisplayName()))
                .lore(plugin.getMessages().lines("gui.hunt-icon-lore", HuntMenu.summary(plugin, hunt)))
                .glint(hunt.isActive())
                .build();
    }
}
