package dev.oakheart.oakhunt.gui;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.service.WorldSync;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Start, end and schedule flows shared by commands and menus. A start on a hunt that has run
 * before asks whether to continue the last run or begin a new one.
 */
public class HuntActions {

    private final OakHunt plugin;

    public HuntActions(OakHunt plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts now, or schedules when {@code at} is given.
     *
     * @param newRun true/false when already chosen, null to ask (or default to new on a first run)
     */
    public void start(CommandSender sender, Hunt hunt, Boolean newRun, Long at) {
        if (hunt.isActive()) {
            plugin.getMessages().cmd(sender, "already-active", Messages.hunt(hunt));
            return;
        }
        if (plugin.getHuntManager().heads(hunt).isEmpty()) {
            plugin.getMessages().cmd(sender, "no-heads", Messages.hunt(hunt));
            return;
        }
        boolean hasRun = plugin.getHuntManager().lastRun(hunt).isPresent();
        if (newRun == null && hasRun) {
            if (sender instanceof Player player) {
                plugin.getMenus().choose(player, "choose-run",
                        () -> start(sender, hunt, false, at), () -> start(sender, hunt, true, at));
            } else {
                plugin.getMessages().cmd(sender, "choose-run-console", Messages.huntId(hunt));
            }
            return;
        }
        boolean fresh = newRun == null || newRun || !hasRun;
        if (at != null) {
            plugin.getHuntManager().scheduleStart(hunt, at, !fresh, sender.getName());
            plugin.getMessages().cmd(sender, "start-scheduled", Messages.hunt(hunt),
                    Messages.text("time", TimeUtil.formatUtc(at)),
                    Messages.text("countdown", TimeUtil.formatDuration(at - System.currentTimeMillis())));
            return;
        }
        plugin.getHuntManager().start(hunt, fresh, sender.getName());
        plugin.getMessages().cmd(sender, fresh ? "started-new" : "started-continued", Messages.hunt(hunt),
                Messages.text("total", plugin.getHuntManager().liveTotal(hunt)));
    }

    public void end(CommandSender sender, Hunt hunt, Long at) {
        if (at != null) {
            plugin.getHuntManager().scheduleEnd(hunt, at, sender.getName());
            plugin.getMessages().cmd(sender, "end-scheduled", Messages.hunt(hunt),
                    Messages.text("time", TimeUtil.formatUtc(at)),
                    Messages.text("countdown", TimeUtil.formatDuration(at - System.currentTimeMillis())));
            return;
        }
        if (!hunt.isActive()) {
            plugin.getMessages().cmd(sender, "not-active", Messages.hunt(hunt));
            return;
        }
        plugin.getHuntManager().end(hunt, sender.getName());
        plugin.getMessages().cmd(sender, "ended", Messages.hunt(hunt));
    }

    public void cancel(CommandSender sender, Hunt hunt) {
        if (hunt.getStartAt() == null && hunt.getEndAt() == null) {
            plugin.getMessages().cmd(sender, "nothing-scheduled", Messages.hunt(hunt));
            return;
        }
        plugin.getHuntManager().cancelSchedule(hunt, sender.getName());
        plugin.getMessages().cmd(sender, "schedule-cancelled", Messages.hunt(hunt));
    }

    /** Runs the start check and reports each conflict. */
    public void check(CommandSender sender, Hunt hunt) {
        plugin.getMessages().cmd(sender, "check-running", Messages.hunt(hunt),
                Messages.text("count", plugin.getHuntManager().heads(hunt).size()));
        plugin.getWorldSync().check(hunt).thenAccept(conflicts -> report(sender, hunt, conflicts));
    }

    private void report(CommandSender sender, Hunt hunt, List<WorldSync.Conflict> conflicts) {
        if (conflicts.isEmpty()) {
            plugin.getMessages().cmd(sender, "check-clear", Messages.hunt(hunt));
            return;
        }
        plugin.getMessages().cmd(sender, "check-conflicts", Messages.hunt(hunt), Messages.text("count", conflicts.size()));
        if (sender instanceof Player player) {
            for (WorldSync.Conflict conflict : conflicts) {
                plugin.getWorldSync().sendConflict(player, hunt, conflict.head(), conflict.occupant());
            }
        }
    }

    /**
     * Parses a date argument and rejects past times.
     *
     * @return epoch millis, or null after telling the sender what went wrong
     */
    public Long parseFuture(CommandSender sender, String input) {
        long now = System.currentTimeMillis();
        Long at = TimeUtil.parse(input, now);
        if (at == null) {
            plugin.getMessages().cmd(sender, "invalid-date", Messages.text("input", input));
            return null;
        }
        if (at <= now) {
            plugin.getMessages().cmd(sender, "date-in-past", Messages.text("time", TimeUtil.formatUtc(at)));
            return null;
        }
        return at;
    }
}
