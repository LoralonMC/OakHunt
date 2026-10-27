package dev.oakheart.oakhunt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.model.Skin;
import dev.oakheart.oakhunt.service.HuntManager;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.SkinUtil;
import dev.oakheart.oakhunt.util.TimeUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /headhunt}: the player journal with no arguments, and every staff action beneath it.
 */
@SuppressWarnings("UnstableApiUsage")
public class HeadHuntCommand {

    private final OakHunt plugin;

    public HeadHuntCommand(OakHunt plugin) {
        this.plugin = plugin;
    }

    public void register() {
        CommandRegistrar.register(plugin, build().build(), "Head hunts: journal and staff tools");
    }

    private LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("headhunt")
                .requires(s -> s.getSender().hasPermission("oakhunt.use"))
                .executes(ctx -> player(ctx, p -> plugin.getMenus().openJournal(p)))
                .then(Commands.literal("journal")
                        .requires(s -> s.getSender() instanceof Player)
                        .executes(ctx -> player(ctx, p -> plugin.getMenus().openJournal(p))))
                .then(Commands.literal("top")
                        .then(huntArg().executes(ctx -> hunt(ctx, (sender, hunt) -> top(sender, hunt)))))
                .then(Commands.literal("list")
                        .requires(perm("oakhunt.edit"))
                        .executes(ctx -> list(ctx.getSource().getSender())))
                .then(Commands.literal("menu")
                        .requires(playerPerm("oakhunt.edit"))
                        .executes(ctx -> player(ctx, p -> plugin.getMenus().openHuntList(p, 0)))
                        .then(huntArg().executes(ctx -> huntPlayer(ctx, (p, hunt) -> plugin.getMenus().openHunt(p, hunt)))))
                .then(Commands.literal("create")
                        .requires(perm("oakhunt.edit"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("edit")
                        .requires(placePerm())
                        .executes(ctx -> player(ctx, this::exitEdit))
                        .then(huntArg().executes(ctx -> huntPlayer(ctx, this::toggleEdit))))
                .then(Commands.literal("head")
                        .requires(placePerm())
                        .then(huntArg()
                                .executes(ctx -> huntPlayer(ctx, (p, hunt) -> givePlacer(p, hunt, "random")))
                                .then(Commands.argument("skin", StringArgumentType.word())
                                        .suggests(skinSuggestions())
                                        .executes(ctx -> huntPlayer(ctx, (p, hunt) ->
                                                givePlacer(p, hunt, StringArgumentType.getString(ctx, "skin")))))))
                .then(Commands.literal("skin")
                        .requires(perm("oakhunt.edit"))
                        .then(huntArg()
                                .then(Commands.literal("list")
                                        .executes(ctx -> hunt(ctx, this::listSkins)))
                                .then(Commands.literal("add")
                                        .executes(ctx -> huntPlayer(ctx, this::addHeldSkin))
                                        .then(Commands.argument("source", StringArgumentType.greedyString())
                                                .executes(ctx -> hunt(ctx, (sender, hunt) ->
                                                        addSkin(sender, hunt, StringArgumentType.getString(ctx, "source"))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("skin", IntegerArgumentType.integer(1))
                                                .executes(ctx -> hunt(ctx, (sender, hunt) ->
                                                        removeSkin(sender, hunt, IntegerArgumentType.getInteger(ctx, "skin"))))))))
                .then(Commands.literal("clue")
                        .requires(playerPerm("oakhunt.edit"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> player(ctx, p -> clue(p, StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("check")
                        .requires(perm("oakhunt.edit"))
                        .then(huntArg().executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().check(sender, hunt)))))
                .then(Commands.literal("conflicts")
                        .requires(playerPerm("oakhunt.edit"))
                        .then(huntArg().executes(ctx -> huntPlayer(ctx, this::conflicts))))
                .then(Commands.literal("undo")
                        .requires(placePerm())
                        .executes(ctx -> player(ctx, this::undo)))
                .then(Commands.literal("tp")
                        .requires(playerPerm("oakhunt.edit"))
                        .then(Commands.argument("head", IntegerArgumentType.integer(1))
                                .executes(ctx -> player(ctx, p -> teleport(p, IntegerArgumentType.getInteger(ctx, "head"))))))
                .then(Commands.literal("removehead")
                        .requires(perm("oakhunt.edit"))
                        .then(Commands.argument("head", IntegerArgumentType.integer(1))
                                .executes(ctx -> removeHead(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "head")))))
                .then(Commands.literal("forceplace")
                        .requires(perm("oakhunt.manage"))
                        .then(Commands.argument("head", IntegerArgumentType.integer(1))
                                .executes(ctx -> forcePlace(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "head")))))
                .then(Commands.literal("start")
                        .requires(perm("oakhunt.manage"))
                        .then(huntArg()
                                .executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().start(sender, hunt, null, null)))
                                .then(Commands.literal("new")
                                        .executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().start(sender, hunt, true, null)))
                                        .then(dateArg().executes(ctx -> hunt(ctx, (sender, hunt) -> startAt(ctx, sender, hunt, true)))))
                                .then(Commands.literal("continue")
                                        .executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().start(sender, hunt, false, null)))
                                        .then(dateArg().executes(ctx -> hunt(ctx, (sender, hunt) -> startAt(ctx, sender, hunt, false)))))
                                .then(dateArg().executes(ctx -> hunt(ctx, (sender, hunt) -> startAt(ctx, sender, hunt, null))))))
                .then(Commands.literal("end")
                        .requires(perm("oakhunt.manage"))
                        .then(huntArg()
                                .executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().end(sender, hunt, null)))
                                .then(dateArg().executes(ctx -> hunt(ctx, (sender, hunt) -> {
                                    Long at = plugin.getHuntActions().parseFuture(sender, StringArgumentType.getString(ctx, "date"));
                                    if (at != null) plugin.getHuntActions().end(sender, hunt, at);
                                })))))
                .then(Commands.literal("cancelprompt")
                        .requires(placePerm())
                        .executes(ctx -> player(ctx, p -> plugin.getChatPrompt().abort(p))))
                .then(Commands.literal("cancel")
                        .requires(perm("oakhunt.manage"))
                        .then(huntArg().executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getHuntActions().cancel(sender, hunt)))))
                .then(Commands.literal("player")
                        .requires(perm("oakhunt.player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                                    return builder.buildFuture();
                                })
                                .then(huntArg()
                                        .then(Commands.literal("info").executes(ctx -> playerCmd(ctx, "info", 0, false)))
                                        .then(Commands.literal("reset").executes(ctx -> playerCmd(ctx, "reset", 0, false)))
                                        .then(Commands.literal("grant")
                                                .then(Commands.literal("all")
                                                        .executes(ctx -> playerCmd(ctx, "grant", Integer.MAX_VALUE, false))
                                                        .then(Commands.literal("pay").requires(perm("oakhunt.rewards.pay"))
                                                                .executes(ctx -> playerCmd(ctx, "grant", Integer.MAX_VALUE, true))))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> playerCmd(ctx, "grant", IntegerArgumentType.getInteger(ctx, "amount"), false))
                                                        .then(Commands.literal("pay").requires(perm("oakhunt.rewards.pay")).executes(ctx ->
                                                                playerCmd(ctx, "grant", IntegerArgumentType.getInteger(ctx, "amount"), true))))))))
                .then(Commands.literal("delete")
                        .requires(perm("oakhunt.admin"))
                        .then(huntArg()
                                .executes(ctx -> hunt(ctx, (sender, hunt) -> plugin.getMessages().cmd(sender, "confirm-delete",
                                        java.util.Map.of("hunt_id", hunt.getId()), Messages.hunt(hunt), Messages.huntId(hunt))))
                                .then(Commands.literal("confirm").executes(ctx -> hunt(ctx, (sender, hunt) -> {
                                    plugin.getHuntManager().archiveHunt(hunt, sender.getName());
                                    plugin.getMessages().cmd(sender, "hunt-archived", Messages.hunt(hunt));
                                })))))
                .then(Commands.literal("reload")
                        .requires(perm("oakhunt.admin"))
                        .executes(ctx -> reload(ctx.getSource().getSender())))
                .then(Commands.literal("import")
                        .requires(perm("oakhunt.admin"))
                        .then(Commands.argument("file", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    plugin.getImportService().files().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    plugin.getImportService().run(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "file"));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    // ---------------------------------------------------------------- tree helpers

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> huntArg() {
        return Commands.argument("hunt", StringArgumentType.word()).suggests(huntSuggestions());
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> dateArg() {
        return Commands.argument("date", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
            builder.suggest("+1h");
            builder.suggest("+1d");
            builder.suggest(TimeUtil.formatUtc(System.currentTimeMillis() + 86_400_000L).replace(" UTC", ""));
            return builder.buildFuture();
        });
    }

    private SuggestionProvider<CommandSourceStack> huntSuggestions() {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (Hunt hunt : plugin.getHuntManager().hunts()) {
                if (hunt.getId().startsWith(typed)) builder.suggest(hunt.getId());
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> skinSuggestions() {
        return (ctx, builder) -> {
            builder.suggest("random");
            plugin.getHuntManager().hunt(StringArgumentType.getString(ctx, "hunt")).ifPresent(hunt ->
                    plugin.getHuntManager().skins(hunt).forEach(skin -> builder.suggest(String.valueOf(skin.getId()))));
            return builder.buildFuture();
        };
    }

    private static java.util.function.Predicate<CommandSourceStack> perm(String permission) {
        return s -> s.getSender().hasPermission(permission);
    }

    /** Editors, and hiders (oakhunt.place): edit mode, placers and undo. */
    private static java.util.function.Predicate<CommandSourceStack> placePerm() {
        return s -> s.getSender() instanceof Player
                && (s.getSender().hasPermission("oakhunt.edit") || s.getSender().hasPermission("oakhunt.place"));
    }

    private static java.util.function.Predicate<CommandSourceStack> playerPerm(String permission) {
        return s -> s.getSender() instanceof Player && s.getSender().hasPermission(permission);
    }

    private int player(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        if (ctx.getSource().getSender() instanceof Player player) {
            action.accept(player);
        } else {
            plugin.getMessages().cmd(ctx.getSource().getSender(), "players-only");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int hunt(CommandContext<CommandSourceStack> ctx, java.util.function.BiConsumer<CommandSender, Hunt> action) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "hunt");
        Optional<Hunt> hunt = plugin.getHuntManager().hunt(id);
        if (hunt.isEmpty()) {
            plugin.getMessages().cmd(sender, "hunt-not-found", Messages.text("hunt_id", id));
        } else {
            action.accept(sender, hunt.get());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int huntPlayer(CommandContext<CommandSourceStack> ctx, java.util.function.BiConsumer<Player, Hunt> action) {
        return hunt(ctx, (sender, hunt) -> {
            if (sender instanceof Player player) {
                action.accept(player, hunt);
            } else {
                plugin.getMessages().cmd(sender, "players-only");
            }
        });
    }

    // ---------------------------------------------------------------- actions

    private int list(CommandSender sender) {
        List<Hunt> hunts = plugin.getHuntManager().hunts();
        if (hunts.isEmpty()) {
            plugin.getMessages().cmd(sender, "list-empty");
            return Command.SINGLE_SUCCESS;
        }
        plugin.getMessages().cmd(sender, "list-header", Messages.text("count", hunts.size()));
        for (Hunt hunt : hunts) {
            plugin.getMessages().cmd(sender, "list-line", Messages.hunt(hunt), Messages.huntId(hunt),
                    Messages.text("state", plugin.getHuntManager().state(hunt).key()),
                    Messages.text("heads", plugin.getHuntManager().heads(hunt).size()),
                    Messages.text("total", plugin.getHuntManager().liveTotal(hunt)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void top(CommandSender sender, Hunt hunt) {
        Run run = plugin.getHuntManager().lastRun(hunt).orElse(null);
        if (run == null) {
            plugin.getMessages().cmd(sender, "top-empty", Messages.hunt(hunt));
            return;
        }
        List<PlayerResult> ranking = plugin.getProgressService().ranking(run);
        plugin.getMessages().cmd(sender, "top-header", Messages.hunt(hunt), Messages.text("run", run.getNumber()));
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            PlayerResult result = ranking.get(i);
            plugin.getMessages().cmd(sender, "top-line", Messages.text("rank", i + 1),
                    Messages.text("player", result.getName()), Messages.text("found", result.getFound()));
        }
        if (ranking.isEmpty()) plugin.getMessages().cmd(sender, "top-empty", Messages.hunt(hunt));
        if (sender instanceof Player player) {
            int position = plugin.getProgressService().position(run, player.getUniqueId());
            if (position > 10) plugin.getMessages().cmd(sender, "top-you", Messages.text("rank", position));
        }
    }

    private int create(CommandSender sender, String input) {
        String id = input.toLowerCase(Locale.ROOT);
        if (!HuntManager.ID_PATTERN.matcher(id).matches()) {
            plugin.getMessages().cmd(sender, "invalid-hunt-id", Messages.text("hunt_id", input));
        } else if (plugin.getHuntManager().huntIncludingArchived(id).isPresent()) {
            plugin.getMessages().cmd(sender, "hunt-exists", Messages.text("hunt_id", id));
        } else {
            Hunt hunt = plugin.getHuntManager().createHunt(id, sender.getName());
            plugin.getMessages().cmd(sender, "hunt-created", java.util.Map.of("hunt_id", hunt.getId()), Messages.hunt(hunt), Messages.huntId(hunt));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void exitEdit(Player player) {
        Optional<Hunt> editing = plugin.getEditorService().editing(player);
        if (editing.isEmpty()) {
            plugin.getMessages().cmd(player, "edit-usage");
            return;
        }
        plugin.getEditorService().exit(player);
        plugin.getMessages().cmd(player, "edit-off", Messages.hunt(editing.get()));
    }

    private void toggleEdit(Player player, Hunt hunt) {
        if (plugin.getEditorService().isEditing(player, hunt.getId())) {
            plugin.getEditorService().exit(player);
            plugin.getMessages().cmd(player, "edit-off", Messages.hunt(hunt));
        } else {
            plugin.getEditorService().enter(player, hunt);
            plugin.getMessages().cmd(player, plugin.getEditorService().isHider(player) ? "edit-on-hider" : "edit-on",
                    Messages.hunt(hunt), Messages.huntId(hunt));
        }
    }

    private void givePlacer(Player player, Hunt hunt, String skinArg) {
        if (plugin.getHuntManager().skins(hunt).isEmpty()) {
            plugin.getMessages().cmd(player, "no-skins", Messages.hunt(hunt), Messages.huntId(hunt));
            return;
        }
        Skin skin = null;
        if (!skinArg.equalsIgnoreCase("random")) {
            try {
                skin = plugin.getHuntManager().skin(Integer.parseInt(skinArg))
                        .filter(s -> s.getHuntId().equals(hunt.getId())).orElse(null);
            } catch (NumberFormatException ignored) {
                // falls through to the not-found message
            }
            if (skin == null) {
                plugin.getMessages().cmd(player, "skin-not-in-set", Messages.text("input", skinArg));
                return;
            }
        }
        player.getInventory().addItem(plugin.getSkinService().placer(hunt, skin));
        plugin.getMessages().cmd(player, "placer-given", Messages.hunt(hunt));
        if (!plugin.getEditorService().isEditing(player, hunt.getId())) {
            plugin.getMessages().cmd(player, "placer-edit-reminder", java.util.Map.of("hunt_id", hunt.getId()), Messages.huntId(hunt));
        }
    }

    private void listSkins(CommandSender sender, Hunt hunt) {
        List<Skin> skins = plugin.getHuntManager().skins(hunt);
        plugin.getMessages().cmd(sender, "skins-header", Messages.hunt(hunt), Messages.text("count", skins.size()));
        for (Skin skin : skins) {
            long used = plugin.getHuntManager().heads(hunt).stream().filter(h -> h.getTexture().equals(skin.getTexture())).count();
            plugin.getMessages().cmd(sender, "skins-line", Messages.text("id", skin.getId()),
                    Messages.text("label", skin.getLabel() == null ? "-" : skin.getLabel()), Messages.text("used", used));
        }
    }

    private void addHeldSkin(Player player, Hunt hunt) {
        String texture = SkinUtil.textureOf(player.getInventory().getItemInMainHand());
        if (texture == null) {
            plugin.getMessages().cmd(player, "hold-a-head");
            return;
        }
        plugin.getHuntManager().addSkin(hunt, texture, "held", player.getName());
        plugin.getMessages().cmd(player, "skin-added", Messages.hunt(hunt));
    }

    private void addSkin(CommandSender sender, Hunt hunt, String source) {
        plugin.getSkinService().resolve(source).thenAccept(resolved -> {
            if (resolved == null) {
                plugin.getMessages().cmd(sender, "skin-not-found", Messages.text("input", source));
                return;
            }
            plugin.getHuntManager().addSkin(hunt, resolved.texture(), resolved.label(), sender.getName());
            plugin.getMessages().cmd(sender, "skin-added", Messages.hunt(hunt));
        });
    }

    private void removeSkin(CommandSender sender, Hunt hunt, int id) {
        Optional<Skin> skin = plugin.getHuntManager().skin(id).filter(s -> s.getHuntId().equals(hunt.getId()));
        if (skin.isEmpty()) {
            plugin.getMessages().cmd(sender, "skin-not-in-set", Messages.text("input", id));
            return;
        }
        plugin.getHuntManager().removeSkin(skin.get(), sender.getName());
        plugin.getMessages().cmd(sender, "skin-removed", Messages.text("id", id));
    }

    private void clue(Player player, String text) {
        Optional<Hunt> editing = plugin.getEditorService().editing(player);
        if (editing.isEmpty()) {
            plugin.getMessages().cmd(player, "clue-needs-edit");
            return;
        }
        Head head = lookedAt(player, editing.get());
        if (head == null) {
            plugin.getMessages().cmd(player, "clue-look-at-head");
            return;
        }
        plugin.getHuntManager().setClue(head, text.equalsIgnoreCase("clear") ? null : text, player.getName());
        plugin.getMessages().cmd(player, "clue-set", Messages.text("number", head.getNumber()));
    }

    /** The hunt head the player is looking at within 6 blocks, real block or fake preview. */
    private Head lookedAt(Player player, Hunt hunt) {
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        for (double d = 0.5; d <= 6; d += 0.25) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            Block block = point.getBlock();
            Head head = plugin.getHuntManager().headAt(block, hunt.getId());
            if (head != null) return head;
            if (block.getType().isOccluding()) return null;
        }
        return null;
    }

    private void conflicts(Player player, Hunt hunt) {
        List<Head> conflicts = plugin.getHuntManager().heads(hunt).stream().filter(Head::isConflict).toList();
        if (conflicts.isEmpty()) {
            plugin.getMessages().cmd(player, "check-clear", Messages.hunt(hunt));
            return;
        }
        for (Head head : conflicts) {
            var world = Bukkit.getWorld(head.getWorld());
            var occupant = world == null ? org.bukkit.Material.BARRIER : world.getBlockAt(head.getX(), head.getY(), head.getZ()).getType();
            plugin.getWorldSync().sendConflict(player, hunt, head, occupant);
        }
    }

    private void undo(Player player) {
        plugin.getEditorService().undo(player).ifPresentOrElse(
                head -> plugin.getMessages().cmd(player, "undone", Messages.text("number", head.getNumber())),
                () -> plugin.getMessages().cmd(player, "nothing-to-undo"));
    }

    private void teleport(Player player, int headId) {
        Optional<Head> head = plugin.getHuntManager().head(headId);
        Location location = head.map(h -> plugin.getWorldSync().location(h)).orElse(null);
        if (location == null) {
            plugin.getMessages().cmd(player, "head-not-found", Messages.text("input", headId));
            return;
        }
        player.teleportAsync(location.add(0, 1, 0));
    }

    private int removeHead(CommandSender sender, int headId) {
        Optional<Head> head = plugin.getHuntManager().head(headId);
        if (head.isEmpty()) {
            plugin.getMessages().cmd(sender, "head-not-found", Messages.text("input", headId));
            return Command.SINGLE_SUCCESS;
        }
        Hunt hunt = plugin.getHuntManager().hunt(head.get().getHuntId()).orElse(null);
        if (hunt == null) return Command.SINGLE_SUCCESS;
        if (sender instanceof Player player) {
            plugin.getEditorActions().remove(player, hunt, head.get());
        } else {
            plugin.getHuntManager().archiveHead(head.get(), sender.getName());
            plugin.getMessages().cmd(sender, "head-removed-gui", Messages.hunt(hunt), Messages.text("number", head.get().getNumber()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int forcePlace(CommandSender sender, int headId) {
        Optional<Head> head = plugin.getHuntManager().head(headId);
        if (head.isEmpty()) {
            plugin.getMessages().cmd(sender, "head-not-found", Messages.text("input", headId));
        } else {
            plugin.getWorldSync().forcePlace(head.get(), sender.getName());
            plugin.getMessages().cmd(sender, "force-placed", Messages.text("number", head.get().getNumber()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void startAt(CommandContext<CommandSourceStack> ctx, CommandSender sender, Hunt hunt, Boolean newRun) {
        Long at = plugin.getHuntActions().parseFuture(sender, StringArgumentType.getString(ctx, "date"));
        if (at != null) plugin.getHuntActions().start(sender, hunt, newRun, at);
    }

    private int playerCmd(CommandContext<CommandSourceStack> ctx, String action, int amount, boolean pay) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            plugin.getMessages().cmd(sender, "player-not-found", Messages.text("player", name));
            return Command.SINGLE_SUCCESS;
        }
        OfflinePlayer player = target;
        return hunt(ctx, (s, hunt) -> {
            Run run = plugin.getHuntManager().lastRun(hunt).orElse(null);
            if (run == null) {
                plugin.getMessages().cmd(s, "top-empty", Messages.hunt(hunt));
                return;
            }
            String playerName = player.getName() != null ? player.getName() : name;
            switch (action) {
                case "info" -> {
                    PlayerResult result = plugin.getProgressService().result(run, player.getUniqueId());
                    plugin.getMessages().cmd(s, "player-info", Messages.hunt(hunt), Messages.text("player", playerName),
                            Messages.text("run", run.getNumber()),
                            Messages.text("found", plugin.getProgressService().found(run, player.getUniqueId(), hunt)),
                            Messages.text("total", plugin.getHuntManager().liveTotal(hunt)),
                            Messages.text("rank", plugin.getProgressService().position(run, player.getUniqueId())),
                            Messages.text("completed", result != null && result.getCompletedAt() != null
                                    ? TimeUtil.formatUtc(result.getCompletedAt()) : "-"));
                }
                case "reset" -> {
                    plugin.getProgressService().reset(player.getUniqueId(), run);
                    plugin.getDatabase().audit(s.getName(), hunt.getId(), "player-reset", playerName);
                    plugin.getMessages().cmd(s, "player-reset", Messages.hunt(hunt), Messages.text("player", playerName));
                }
                case "grant" -> {
                    if (!run.isOpen()) {
                        plugin.getMessages().cmd(s, "not-active", Messages.hunt(hunt));
                        return;
                    }
                    int found = plugin.getProgressService().grant(player.getUniqueId(), playerName, hunt, run, amount);
                    plugin.getDatabase().audit(s.getName(), hunt.getId(), "player-grant",
                            playerName + " -> " + found + (pay ? " (paid)" : ""));
                    Player online = player.getPlayer();
                    if (pay && online != null) {
                        plugin.getRewardService().checkMilestones(online, hunt, run);
                    } else if (!pay) {
                        // Mark crossed milestones paid so a later find doesn't pay them retroactively.
                        int total = plugin.getHuntManager().liveTotal(hunt);
                        plugin.getHuntManager().milestones(hunt).stream()
                                .filter(m -> m.isReached(found, total))
                                .filter(m -> !plugin.getProgressService().hasPaid(run, player.getUniqueId(), m.getId()))
                                .forEach(m -> plugin.getProgressService().markPaid(run, player.getUniqueId(), m.getId()));
                    }
                    plugin.getMessages().cmd(s, pay && online == null ? "player-granted-offline" : "player-granted",
                            Messages.hunt(hunt), Messages.text("player", playerName), Messages.text("found", found),
                            Messages.text("total", plugin.getHuntManager().liveTotal(hunt)));
                }
                default -> { }
            }
        });
    }

    private int reload(CommandSender sender) {
        try {
            if (plugin.reload()) {
                plugin.getMessages().cmd(sender, "reload-success");
            } else {
                plugin.getMessages().cmd(sender, "reload-failed");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
            plugin.getMessages().cmd(sender, "reload-failed");
        }
        return Command.SINGLE_SUCCESS;
    }
}
