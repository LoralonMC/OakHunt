package dev.oakheart.oakhunt.service;

import dev.oakheart.config.ConfigManager;
import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.ClueMode;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntSetting;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.oakhunt.util.TimeUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Edit files: a hunt's editable parts (name, description, settings, milestones with their
 * messages and rewards, clues) as YAML in {@code plugins/OakHunt/edits/<hunt>.yml}, so they can be
 * edited outside the game and reviewed in git. Heads, skins, schedules, runs and progress are not
 * in the file and are never changed by it.
 *
 * <p>The database stays the source of truth. An export carries a fingerprint of the hunt; apply
 * refuses when the hunt has changed in-game since, so a stale file can't overwrite GUI edits.
 * Apply always previews first; the [Confirm] carries a hash of the previewed file, so what gets
 * applied is exactly what was shown.
 */
public class HuntFileService {

    private static final String HEADER = """
            # OakHunt edit file. Edit it, then run /headhunt apply <hunt> to review and apply.
            # Only what is here can change: heads, skins, schedules, runs and progress are never touched.
            #
            # message: null uses the default message. broadcast: null means no broadcast.
            # Milestones are keyed by number of finds, or 'all' for completion. Remove one to delete it.
            # Reward types: executable-item, nexo-item, crate-key, tag (granted directly), tag-voucher,
            #   command (runs as console, {player} is the player's name), item (a copy of a held
            #   item: keep or remove it, don't edit it).
            # clues: head number to clue. Remove a line to clear that clue.
            #
            # Leave 'fingerprint' as it is: apply refuses if the hunt was changed in-game after this
            # export, so nobody's GUI edits get overwritten.
            """;

    private record RewardSpec(RewardType type, String value, int amount, String item) {
    }

    private record MilestoneSpec(String message, String broadcast, List<RewardSpec> rewards) {
    }

    /** Everything an edit file can change. Its written form is what the fingerprint hashes. */
    private record Spec(String displayName, String description, Map<String, String> settings,
                        TreeMap<Integer, MilestoneSpec> milestones, TreeMap<Integer, String> clues) {
    }

    /** One line of the preview, and what it changes (null for detail lines under another change). */
    private record Change(String key, TagResolver[] resolvers, Runnable action) {
    }

    private final OakHunt plugin;
    private final File folder;

    public HuntFileService(OakHunt plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "edits");
    }

    // ---------------------------------------------------------------- export

    /**
     * @param overwrite replace a file that has edits not yet applied
     */
    public void export(CommandSender sender, Hunt hunt, boolean overwrite) {
        File file = file(hunt);
        if (!overwrite && file.isFile() && hasPendingEdits(file, hunt)) {
            plugin.getMessages().cmd(sender, "export-pending", Map.of("hunt_id", hunt.getId()),
                    Messages.hunt(hunt), Messages.text("file", file.getName()));
            return;
        }
        if (!write(hunt, file)) {
            plugin.getMessages().cmd(sender, "export-failed", Messages.text("file", file.getName()));
            return;
        }
        plugin.getDatabase().audit(sender.getName(), hunt.getId(), "export", file.getName());
        plugin.getMessages().cmd(sender, "export-done", Messages.hunt(hunt), Messages.huntId(hunt),
                Messages.text("file", file.getName()));
    }

    /** The file was edited (or no longer reads cleanly) since its own export. */
    private boolean hasPendingEdits(File file, Hunt hunt) {
        ConfigManager data = load(file);
        if (data == null) return true;
        List<String> problems = new ArrayList<>();
        Spec spec = parse(data, hunt, problems);
        return !problems.isEmpty() || !fingerprint(spec).equals(data.getString("fingerprint", ""));
    }

    private boolean write(Hunt hunt, File file) {
        Spec spec = current(hunt);
        String text = HEADER
                + "hunt: " + quote(hunt.getId()) + "\n"
                + "fingerprint: " + quote(fingerprint(spec)) + "\n"
                + "exported: " + quote(TimeUtil.formatUtc(System.currentTimeMillis())) + "\n\n"
                + body(spec);
        try {
            Files.createDirectories(folder.toPath());
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write edit file " + file.getName(), e);
            return false;
        }
    }

    // ---------------------------------------------------------------- apply

    /**
     * Without a hash, previews the changes and offers [Confirm]. With one, applies them if the
     * file still matches the preview.
     */
    public void apply(CommandSender sender, Hunt hunt, String confirmHash) {
        File file = file(hunt);
        if (!file.isFile()) {
            plugin.getMessages().cmd(sender, "apply-missing", Messages.huntId(hunt), Messages.text("file", file.getName()));
            return;
        }
        ConfigManager data = load(file);
        if (data == null) {
            plugin.getMessages().cmd(sender, "apply-invalid", Messages.text("file", file.getName()), Messages.text("count", 1));
            plugin.getMessages().cmd(sender, "apply-problem", Messages.text("problem", "the file isn't valid YAML, see console"));
            return;
        }
        List<String> problems = new ArrayList<>();
        Spec desired = parse(data, hunt, problems);
        if (!problems.isEmpty()) {
            plugin.getMessages().cmd(sender, "apply-invalid", Messages.text("file", file.getName()), Messages.text("count", problems.size()));
            problems.stream().limit(15).forEach(p -> plugin.getMessages().cmd(sender, "apply-problem", Messages.text("problem", p)));
            return;
        }
        if (!fingerprint(current(hunt)).equals(data.getString("fingerprint", ""))) {
            plugin.getMessages().cmd(sender, "apply-stale", Messages.hunt(hunt), Messages.huntId(hunt));
            return;
        }
        String hash = fingerprint(desired);
        if (confirmHash != null && !confirmHash.equals(hash)) {
            plugin.getMessages().cmd(sender, "apply-moved", Messages.text("file", file.getName()), Messages.huntId(hunt));
            return;
        }
        List<Change> changes = diff(hunt, desired, sender.getName());
        if (changes.isEmpty()) {
            plugin.getMessages().cmd(sender, "apply-no-changes", Messages.hunt(hunt), Messages.text("file", file.getName()));
            return;
        }
        long count = changes.stream().filter(c -> c.action() != null).count();
        if (confirmHash == null) {
            plugin.getMessages().cmd(sender, "apply-preview-header", Messages.hunt(hunt), Messages.text("count", count));
            for (Change change : changes) plugin.getMessages().cmd(sender, change.key(), change.resolvers());
            plugin.getMessages().cmd(sender, "apply-preview-confirm", Map.of("hunt_id", hunt.getId(), "hash", hash));
            return;
        }
        for (Change change : changes) {
            if (change.action() != null) change.action().run();
        }
        plugin.getDatabase().audit(sender.getName(), hunt.getId(), "apply", count + " change(s) from edits/" + file.getName());
        // Fresh fingerprint, so the file is ready for the next edit.
        write(hunt, file);
        plugin.getMessages().cmd(sender, "apply-done", Messages.hunt(hunt), Messages.text("count", count));
    }

    private List<Change> diff(Hunt hunt, Spec desired, String actor) {
        HuntManager manager = plugin.getHuntManager();
        List<Change> changes = new ArrayList<>();

        if (!desired.displayName().equals(hunt.getDisplayName())) {
            changes.add(new Change("apply-change-name", resolvers(Messages.mini("name", desired.displayName())), () -> {
                hunt.setDisplayName(desired.displayName());
                manager.saveHunt(hunt);
                plugin.getDatabase().audit(actor, hunt.getId(), "rename", desired.displayName());
            }));
        }
        if (!Objects.equals(desired.description(), hunt.getDescription())) {
            changes.add(new Change(desired.description() == null ? "apply-change-description-cleared" : "apply-change-description",
                    resolvers(Messages.mini("description", desired.description())), () -> {
                hunt.setDescription(desired.description());
                manager.saveHunt(hunt);
                plugin.getDatabase().audit(actor, hunt.getId(), "description", String.valueOf(desired.description()));
            }));
        }
        for (Map.Entry<String, String> entry : desired.settings().entrySet()) {
            HuntSetting setting = setting(entry.getKey());
            String old = hunt.settingString(setting);
            if (entry.getValue().equals(old)) continue;
            changes.add(new Change("apply-change-setting", resolvers(Messages.text("setting", setting.key()),
                    Messages.text("old", old), Messages.text("new", entry.getValue())),
                    () -> manager.setSetting(hunt, setting, entry.getValue(), actor)));
        }

        Map<Integer, Milestone> existing = new LinkedHashMap<>();
        for (Milestone milestone : manager.milestones(hunt)) existing.put(milestone.getThreshold(), milestone);
        for (Milestone milestone : existing.values()) {
            if (desired.milestones().containsKey(milestone.getThreshold())) continue;
            changes.add(new Change("apply-change-milestone-removed", resolvers(label(milestone.getThreshold()),
                    Messages.text("rewards", milestone.getRewards().size())), () -> manager.removeMilestone(milestone, actor)));
        }
        for (Map.Entry<Integer, MilestoneSpec> entry : desired.milestones().entrySet()) {
            int threshold = entry.getKey();
            MilestoneSpec want = entry.getValue();
            Milestone have = existing.get(threshold);
            if (have == null) {
                changes.add(new Change("apply-change-milestone-added", resolvers(label(threshold)), () ->
                        manager.addMilestone(hunt, threshold, actor).ifPresent(milestone -> {
                            milestone.setMessage(want.message());
                            milestone.setBroadcast(want.broadcast());
                            manager.saveMilestone(milestone, actor);
                            for (RewardSpec reward : want.rewards()) addReward(milestone, reward, actor);
                        })));
                if (want.message() != null) changes.add(detail("apply-change-message", threshold, want.message()));
                if (want.broadcast() != null) changes.add(detail("apply-change-broadcast", threshold, want.broadcast()));
                for (RewardSpec reward : want.rewards()) {
                    changes.add(new Change("apply-change-reward-added", resolvers(label(threshold),
                            Messages.text("reward", describe(reward))), null));
                }
                continue;
            }
            if (!Objects.equals(want.message(), have.getMessage())) {
                changes.add(new Change(want.message() == null ? "apply-change-message-default" : "apply-change-message",
                        resolvers(label(threshold), Messages.mini("value", want.message())), () -> {
                    have.setMessage(want.message());
                    manager.saveMilestone(have, actor);
                }));
            }
            if (!Objects.equals(want.broadcast(), have.getBroadcast())) {
                changes.add(new Change(want.broadcast() == null ? "apply-change-broadcast-off" : "apply-change-broadcast",
                        resolvers(label(threshold), Messages.mini("value", want.broadcast())), () -> {
                    have.setBroadcast(want.broadcast());
                    manager.saveMilestone(have, actor);
                }));
            }
            // Rewards match as a multiset: unchanged ones keep their ids, the rest are removed or added.
            List<RewardSpec> toAdd = new ArrayList<>(want.rewards());
            for (Reward reward : List.copyOf(have.getRewards())) {
                if (toAdd.remove(spec(reward))) continue;
                changes.add(new Change("apply-change-reward-removed", resolvers(label(threshold),
                        Messages.text("reward", plugin.getRewardService().describe(reward))),
                        () -> manager.removeReward(have, reward, actor)));
            }
            for (RewardSpec reward : toAdd) {
                changes.add(new Change("apply-change-reward-added", resolvers(label(threshold),
                        Messages.text("reward", describe(reward))), () -> addReward(have, reward, actor)));
            }
        }

        for (Head head : manager.heads(hunt)) {
            String want = desired.clues().get(head.getNumber());
            if (Objects.equals(want, head.getClue())) continue;
            changes.add(new Change(want == null ? "apply-change-clue-cleared" : "apply-change-clue",
                    resolvers(Messages.text("number", head.getNumber()), Messages.mini("clue", want)),
                    () -> manager.setClue(head, want, actor)));
        }
        return changes;
    }

    private Change detail(String key, int threshold, String value) {
        return new Change(key, resolvers(label(threshold), Messages.mini("value", value)), null);
    }

    private void addReward(Milestone milestone, RewardSpec reward, String actor) {
        byte[] item = reward.item() == null ? null : Base64.getDecoder().decode(reward.item());
        plugin.getHuntManager().addReward(milestone, reward.type(), reward.value(), reward.amount(), item, actor);
    }

    private String describe(RewardSpec reward) {
        byte[] item = reward.item() == null ? null : Base64.getDecoder().decode(reward.item());
        return plugin.getRewardService().describe(new Reward(0, 0, reward.type(), reward.value(), reward.amount(), item));
    }

    // ---------------------------------------------------------------- model

    private Spec current(Hunt hunt) {
        Map<String, String> settings = new LinkedHashMap<>();
        for (HuntSetting setting : HuntSetting.values()) settings.put(setting.key(), hunt.settingString(setting));
        TreeMap<Integer, MilestoneSpec> milestones = milestoneMap();
        for (Milestone milestone : plugin.getHuntManager().milestones(hunt)) {
            List<RewardSpec> rewards = new ArrayList<>();
            for (Reward reward : milestone.getRewards()) rewards.add(spec(reward));
            milestones.put(milestone.getThreshold(), new MilestoneSpec(blankToNull(milestone.getMessage()),
                    blankToNull(milestone.getBroadcast()), rewards));
        }
        TreeMap<Integer, String> clues = new TreeMap<>();
        for (Head head : plugin.getHuntManager().heads(hunt)) {
            if (head.getClue() != null) clues.put(head.getNumber(), head.getClue());
        }
        return new Spec(hunt.getDisplayName(), blankToNull(hunt.getDescription()), settings, milestones, clues);
    }

    private static RewardSpec spec(Reward reward) {
        return new RewardSpec(reward.type(), reward.value(), reward.amount(),
                reward.item() == null ? null : Base64.getEncoder().encodeToString(reward.item()));
    }

    private Spec parse(ConfigManager data, Hunt hunt, List<String> problems) {
        if (!hunt.getId().equals(data.getString("hunt", ""))) {
            problems.add("hunt: this file is for '" + data.getString("hunt", "") + "', not '" + hunt.getId() + "'");
        }
        String displayName = blankToNull(data.getString("display-name", null));
        if (displayName == null) problems.add("display-name: can't be empty");

        Map<String, String> settings = new LinkedHashMap<>();
        for (String key : data.getKeys("settings", false)) {
            HuntSetting setting = setting(key);
            String value = data.getString("settings." + key, "").trim();
            if (setting == null) {
                problems.add("settings." + key + ": unknown setting");
            } else if (setting == HuntSetting.CLUE_MODE) {
                String mode = value.toUpperCase(Locale.ROOT);
                if (List.of(ClueMode.values()).stream().noneMatch(m -> m.name().equals(mode))) {
                    problems.add("settings.clue-mode: '" + value + "' isn't all, every_n or milestone");
                }
                settings.put(key, mode);
            } else if (setting == HuntSetting.CLUE_EVERY) {
                if (positive(value) == null) problems.add("settings.clue-every: '" + value + "' isn't a whole number above 0");
                settings.put(key, value);
            } else {
                String bool = value.toLowerCase(Locale.ROOT);
                if (!bool.equals("true") && !bool.equals("false")) problems.add("settings." + key + ": '" + value + "' isn't true or false");
                settings.put(key, bool);
            }
        }

        TreeMap<Integer, MilestoneSpec> milestones = milestoneMap();
        for (String key : data.getKeys("milestones", false)) {
            String path = "milestones." + key;
            Integer threshold = key.equalsIgnoreCase("all") ? Integer.valueOf(Milestone.ALL) : positive(key);
            if (threshold == null) {
                problems.add(path + ": milestones are a number of finds, or all");
                continue;
            }
            List<RewardSpec> rewards = new ArrayList<>();
            List<Map<String, Object>> entries = data.getMapList(path + ".rewards");
            for (int i = 0; i < entries.size(); i++) {
                RewardSpec reward = reward(entries.get(i), path + ".rewards[" + (i + 1) + "]", problems);
                if (reward != null) rewards.add(reward);
            }
            milestones.put(threshold, new MilestoneSpec(blankToNull(data.getString(path + ".message", null)),
                    blankToNull(data.getString(path + ".broadcast", null)), rewards));
        }

        TreeMap<Integer, String> clues = new TreeMap<>();
        List<Integer> numbers = plugin.getHuntManager().heads(hunt).stream().map(Head::getNumber).toList();
        for (String key : data.getKeys("clues", false)) {
            Integer number = positive(key);
            String clue = blankToNull(data.getString("clues." + key, null));
            if (number == null || !numbers.contains(number)) {
                problems.add("clues." + key + ": the hunt has no head with that number");
            } else if (clue != null) {
                clues.put(number, clue);
            }
        }
        return new Spec(displayName == null ? "" : displayName, blankToNull(data.getString("description", null)),
                settings, milestones, clues);
    }

    private RewardSpec reward(Map<String, Object> entry, String path, List<String> problems) {
        String typeName = String.valueOf(entry.getOrDefault("type", ""));
        RewardType type;
        try {
            type = RewardType.valueOf(typeName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            problems.add(path + ": unknown reward type '" + typeName + "'");
            return null;
        }
        String value = entry.get("value") == null ? null : blankToNull(String.valueOf(entry.get("value")));
        Integer amount = entry.get("amount") == null ? Integer.valueOf(1) : positive(String.valueOf(entry.get("amount")));
        if (amount == null) {
            problems.add(path + ": amount '" + entry.get("amount") + "' isn't a whole number above 0");
            return null;
        }
        String item = null;
        String problem = switch (type) {
            case EXECUTABLE_ITEM -> !plugin.getExecutableItemsHook().isAvailable() ? "ExecutableItems isn't available"
                    : plugin.getExecutableItemsHook().build(value, 1, null) == null ? "no ExecutableItems item '" + value + "'" : null;
            case NEXO_ITEM -> !plugin.getNexoHook().isAvailable() ? "Nexo isn't available"
                    : plugin.getNexoHook().build(value, 1) == null ? "no Nexo item '" + value + "'" : null;
            case CRATE_KEY -> plugin.getRewardService().isValidKeyId(value) && plugin.getRewardService().crateKeys().contains(value)
                    ? null : "no crate key '" + value + "'";
            case TAG, TAG_VOUCHER -> !plugin.getOakTagsHook().isAvailable() ? "OakTags isn't available"
                    : plugin.getOakTagsHook().isGrantable(value) ? null : "no grantable chat tag '" + value + "'";
            case COMMAND -> value == null ? "a command reward needs a value" : null;
            case ITEM -> {
                item = entry.get("item") == null ? null : String.valueOf(entry.get("item"));
                yield validItem(item) ? null : "the item copy is missing or damaged; remove the reward and add it in-game";
            }
        };
        if (problem != null) {
            problems.add(path + ": " + problem);
            return null;
        }
        if (type == RewardType.COMMAND && value.startsWith("/")) value = value.substring(1);
        if (type == RewardType.ITEM) value = null;
        if (type == RewardType.TAG) amount = 1;
        return new RewardSpec(type, value, amount, item);
    }

    private static boolean validItem(String base64) {
        if (base64 == null) return false;
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
            return stack != null && !stack.getType().isAir();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- writing

    private static String body(Spec spec) {
        StringBuilder out = new StringBuilder();
        out.append("display-name: ").append(quote(spec.displayName())).append('\n');
        out.append("description: ").append(quote(spec.description())).append("\n\n");
        out.append("settings:\n");
        for (Map.Entry<String, String> entry : spec.settings().entrySet()) {
            String value = entry.getValue();
            boolean bare = value.equals("true") || value.equals("false") || value.matches("\\d+");
            out.append("  ").append(entry.getKey()).append(": ").append(bare ? value : quote(value)).append('\n');
        }
        out.append("\nmilestones:\n");
        for (Map.Entry<Integer, MilestoneSpec> entry : spec.milestones().entrySet()) {
            MilestoneSpec milestone = entry.getValue();
            out.append("  ").append(entry.getKey() == Milestone.ALL ? "all" : entry.getKey()).append(":\n");
            out.append("    message: ").append(quote(milestone.message())).append('\n');
            out.append("    broadcast: ").append(quote(milestone.broadcast())).append('\n');
            if (milestone.rewards().isEmpty()) continue;
            out.append("    rewards:\n");
            for (RewardSpec reward : milestone.rewards()) {
                out.append("      - type: ").append(reward.type().name().toLowerCase(Locale.ROOT).replace('_', '-')).append('\n');
                if (reward.value() != null) out.append("        value: ").append(quote(reward.value())).append('\n');
                if (reward.type() != RewardType.TAG) out.append("        amount: ").append(reward.amount()).append('\n');
                if (reward.item() != null) out.append("        item: ").append(quote(reward.item())).append('\n');
            }
        }
        out.append("\nclues:\n");
        for (Map.Entry<Integer, String> entry : spec.clues().entrySet()) {
            out.append("  ").append(entry.getKey()).append(": ").append(quote(entry.getValue())).append('\n');
        }
        return out.toString();
    }

    private static String fingerprint(Spec spec) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body(spec).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Single-quoted YAML scalar, or null. This parser reads one line per scalar. */
    private static String quote(String value) {
        if (value == null) return "null";
        return "'" + value.replace("'", "''").replace("\n", "<newline>") + "'";
    }

    // ---------------------------------------------------------------- helpers

    private File file(Hunt hunt) {
        return new File(folder, hunt.getId() + ".yml");
    }

    private ConfigManager load(File file) {
        try {
            return ConfigManager.load(file.toPath());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read edit file " + file.getName(), e);
            return null;
        }
    }

    /** Numbered milestones ascending, "all" last. */
    private static TreeMap<Integer, MilestoneSpec> milestoneMap() {
        return new TreeMap<>(Comparator.comparingInt(t -> t == Milestone.ALL ? Integer.MAX_VALUE : t));
    }

    private static HuntSetting setting(String key) {
        for (HuntSetting setting : HuntSetting.values()) {
            if (setting.key().equals(key)) return setting;
        }
        return null;
    }

    private static Integer positive(String value) {
        try {
            int number = Integer.parseInt(value.trim());
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static TagResolver label(int threshold) {
        return Messages.text("milestone", threshold == Milestone.ALL ? "all" : String.valueOf(threshold));
    }

    private static TagResolver[] resolvers(TagResolver... resolvers) {
        return resolvers;
    }
}
