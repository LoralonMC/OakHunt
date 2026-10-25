package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntState;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.TimeUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One hunt's control page: start/end, edit mode, skins, heads, rewards, settings, runs.
 */
public class HuntMenu extends Menu {

    private final Hunt hunt;

    public HuntMenu(OakHunt plugin, Player viewer, Hunt hunt) {
        super(plugin, viewer, 5, "gui.hunt-title", Messages.text("hunt_id", hunt.getId()));
        this.hunt = hunt;
    }

    @Override
    protected void draw() {
        set(4, HuntListMenu.icon(plugin, hunt));
        TagResolver[] summary = summary(plugin, hunt);

        if (hunt.isActive()) {
            set(19, button(Material.RED_CONCRETE, "end-hunt", summary), e -> {
                if (!require("oakhunt.manage")) return;
                if (e.isRightClick()) {
                    plugin.getChatPrompt().ask(viewer, "prompt-date", input -> {
                        if (input == null) return;
                        Long at = plugin.getHuntActions().parseFuture(viewer, input);
                        if (at != null) plugin.getHuntActions().end(viewer, hunt, at);
                    });
                } else {
                    plugin.getMenus().choose(viewer, "confirm-end", () -> plugin.getHuntActions().end(viewer, hunt, null), null);
                }
            });
        } else {
            set(19, button(Material.LIME_CONCRETE, "start-hunt", summary), e -> {
                if (!require("oakhunt.manage")) return;
                if (e.isRightClick()) {
                    plugin.getChatPrompt().ask(viewer, "prompt-date", input -> {
                        if (input == null) return;
                        Long at = plugin.getHuntActions().parseFuture(viewer, input);
                        if (at != null) plugin.getHuntActions().start(viewer, hunt, null, at);
                    });
                } else {
                    viewer.closeInventory();
                    plugin.getHuntActions().start(viewer, hunt, null, null);
                }
            });
        }
        if (hunt.getStartAt() != null || hunt.getEndAt() != null) {
            set(28, button(Material.CLOCK, "cancel-schedule", summary), e -> {
                if (!require("oakhunt.manage")) return;
                plugin.getHuntActions().cancel(viewer, hunt);
                redraw();
            });
        }
        boolean editing = plugin.getEditorService().isEditing(viewer, hunt.getId());
        set(20, button(editing ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                editing ? "edit-mode-on" : "edit-mode-off"), e -> {
            if (!require("oakhunt.edit")) return;
            if (editing) {
                plugin.getEditorService().exit(viewer);
                plugin.getMessages().cmd(viewer, "edit-off", Messages.hunt(hunt));
            } else {
                plugin.getEditorService().enter(viewer, hunt);
                plugin.getMessages().cmd(viewer, "edit-on", Messages.hunt(hunt), Messages.huntId(hunt));
            }
            redraw();
        });
        set(21, button(Material.PLAYER_HEAD, "skins", Messages.text("count", plugin.getHuntManager().skins(hunt).size())),
                e -> plugin.getMenus().openSkins(viewer, hunt, 0));
        set(22, button(Material.CHEST, "heads", summary), e -> plugin.getMenus().openHeads(viewer, hunt, 0));
        set(23, button(Material.SPYGLASS, "check"), e -> {
            if (!require("oakhunt.edit")) return;
            viewer.closeInventory();
            plugin.getHuntActions().check(viewer, hunt);
        });
        set(24, button(Material.GOLD_INGOT, "rewards", Messages.text("count", plugin.getHuntManager().milestones(hunt).size())),
                e -> {
                    if (require("oakhunt.manage")) plugin.getMenus().openRewards(viewer, hunt);
                });
        set(25, button(Material.COMPARATOR, "settings"), e -> {
            if (require("oakhunt.manage")) plugin.getMenus().openSettings(viewer, hunt);
        });
        set(30, button(Material.BOOK, "runs", Messages.text("count", plugin.getHuntManager().runs(hunt).size())),
                e -> plugin.getMenus().openRuns(viewer, hunt));
        set(32, button(Material.NAME_TAG, "name-description", Messages.mini("name", hunt.getDisplayName()),
                Messages.text("description", hunt.getDescription() == null ? "-" : hunt.getDescription())), e -> {
            if (!require("oakhunt.edit")) return;
            boolean description = e.isRightClick();
            plugin.getChatPrompt().ask(viewer, description ? "prompt-description" : "prompt-rename", input -> {
                if (input != null) {
                    if (description) {
                        hunt.setDescription(input.equalsIgnoreCase("clear") ? null : input);
                    } else {
                        hunt.setDisplayName(input);
                    }
                    plugin.getHuntManager().saveHunt(hunt);
                    plugin.getDatabase().audit(viewer.getName(), hunt.getId(), description ? "description" : "rename", input);
                    plugin.getMessages().cmd(viewer, "hunt-updated", Messages.hunt(hunt));
                }
                plugin.getMenus().openHunt(viewer, hunt);
            });
        });
        back(36, () -> plugin.getMenus().openHuntList(viewer, 0));
        set(44, button(Material.BARRIER, "archive-hunt"), e -> {
            if (!require("oakhunt.admin")) return;
            plugin.getMenus().choose(viewer, "confirm-archive", () -> {
                plugin.getHuntManager().archiveHunt(hunt, viewer.getName());
                plugin.getMessages().cmd(viewer, "hunt-archived", Messages.hunt(hunt));
            }, () -> plugin.getMenus().openHunt(viewer, hunt));
        });
    }

    private boolean require(String permission) {
        if (viewer.hasPermission(permission)) return true;
        plugin.getMessages().cmd(viewer, "no-permission");
        return false;
    }

    /** Placeholders describing a hunt's current state, shared by icons and buttons. */
    static TagResolver[] summary(OakHunt plugin, Hunt hunt) {
        HuntState state = plugin.getHuntManager().state(hunt);
        int heads = plugin.getHuntManager().heads(hunt).size();
        long conflicts = plugin.getHuntManager().heads(hunt).stream().filter(Head::isConflict).count();
        String run = plugin.getHuntManager().lastRun(hunt).map(Run::getNumber).map(String::valueOf).orElse("-");
        long now = System.currentTimeMillis();
        String schedule;
        if (hunt.getStartAt() != null) {
            schedule = plugin.getMessages().manager().getMessageText("gui.schedule-starts")
                    .replace("<time>", TimeUtil.formatUtc(hunt.getStartAt()))
                    .replace("<countdown>", TimeUtil.formatDuration(hunt.getStartAt() - now));
        } else if (hunt.getEndAt() != null) {
            schedule = plugin.getMessages().manager().getMessageText("gui.schedule-ends")
                    .replace("<time>", TimeUtil.formatUtc(hunt.getEndAt()))
                    .replace("<countdown>", TimeUtil.formatDuration(hunt.getEndAt() - now));
        } else {
            schedule = plugin.getMessages().manager().getMessageText("gui.schedule-none");
        }
        return new TagResolver[]{
                Messages.hunt(hunt), Messages.huntId(hunt),
                Messages.mini("state", plugin.getMessages().manager().getMessageText("gui.state-" + state.key())),
                Messages.text("heads", heads), Messages.text("total", plugin.getHuntManager().liveTotal(hunt)),
                Messages.text("conflicts", conflicts), Messages.text("run", run), Messages.mini("schedule", schedule)
        };
    }
}
