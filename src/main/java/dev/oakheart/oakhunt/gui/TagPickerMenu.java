package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.hook.OakTagsHook.TagChoice;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.util.ItemBuilder;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Lists OakTags tags that can be granted. Left-click grants the tag directly; right-click gives
 * a voucher instead (tradeable, and goes to OakOverflow if the inventory is full).
 */
public class TagPickerMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final Hunt hunt;
    private final Milestone milestone;
    private final int page;

    public TagPickerMenu(OakHunt plugin, Player viewer, Hunt hunt, Milestone milestone, int page) {
        super(plugin, viewer, 6, "gui.tags-title");
        this.hunt = hunt;
        this.milestone = milestone;
        this.page = page;
    }

    @Override
    protected void draw() {
        List<TagChoice> tags = plugin.getOakTagsHook().grantableTags();
        if (tags.isEmpty()) set(22, button(Material.BARRIER, "no-tags"));
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < tags.size(); i++) {
            TagChoice tag = tags.get(from + i);
            set(i, ItemBuilder.of(Material.NAME_TAG)
                    .name(plugin.getMessages().line("gui.tag-icon-name", Messages.mini("tag", tag.display())))
                    .lore(plugin.getMessages().lines("gui.tag-icon-lore", Messages.text("tag_id", tag.id())))
                    .build(), e -> {
                RewardType type = e.isRightClick() ? RewardType.TAG_VOUCHER : RewardType.TAG;
                plugin.getHuntManager().addReward(milestone, type, tag.id(), 1, null, viewer.getName());
                plugin.getMessages().cmd(viewer, "reward-added");
                plugin.getMenus().openMilestone(viewer, hunt, milestone);
            });
        }
        back(45, () -> plugin.getMenus().openRewardType(viewer, hunt, milestone));
        pager(page, pages(tags.size(), PER_PAGE), 48, 50, p -> plugin.getMenus().openTagPicker(viewer, hunt, milestone, p));
    }
}
