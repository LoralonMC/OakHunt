package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.BlockKey;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.HuntSetting;
import dev.oakheart.oakhunt.model.HuntState;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.model.Skin;
import dev.oakheart.oakhunt.storage.HuntDatabase;
import dev.oakheart.oakhunt.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Owns hunt definitions in memory (hunts, heads, skins, runs, milestones) and every state change.
 * Each change is written through to the database and pushed to the world and editors.
 */
public class HuntManager {

    /** Hunt ids: lowercase letters, digits and hyphens. No underscores, so placeholders can split on them. */
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    private final OakHunt plugin;
    private final HuntDatabase db;

    private final Map<String, Hunt> hunts = new LinkedHashMap<>();
    private final Map<Integer, Head> heads = new HashMap<>();
    private final Map<String, List<Head>> headsByHunt = new HashMap<>();
    private final Map<BlockKey, Head> headsByBlock = new HashMap<>();
    private final Map<String, Map<Long, List<Head>>> headsByChunk = new HashMap<>();
    private final Map<String, List<Skin>> skinsByHunt = new HashMap<>();
    private final Map<Integer, Skin> skins = new HashMap<>();
    private final Map<String, List<Run>> runsByHunt = new HashMap<>();
    private final Map<Integer, Run> runs = new HashMap<>();
    private final Map<String, List<Milestone>> milestonesByHunt = new HashMap<>();
    private final Map<Integer, Milestone> milestones = new HashMap<>();

    private BukkitTask scheduleTask;

    public HuntManager(OakHunt plugin, HuntDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ---------------------------------------------------------------- lifecycle

    public void load() throws SQLException {
        for (Hunt hunt : db.loadHunts()) hunts.put(hunt.getId(), hunt);
        for (Skin skin : db.loadSkins()) {
            skins.put(skin.getId(), skin);
            skinsByHunt.computeIfAbsent(skin.getHuntId(), k -> new ArrayList<>()).add(skin);
        }
        for (Head head : db.loadHeads()) index(head);
        for (Run run : db.loadRuns()) {
            runs.put(run.getId(), run);
            runsByHunt.computeIfAbsent(run.getHuntId(), k -> new ArrayList<>()).add(run);
        }
        for (Milestone milestone : db.loadMilestones()) {
            milestones.put(milestone.getId(), milestone);
            milestonesByHunt.computeIfAbsent(milestone.getHuntId(), k -> new ArrayList<>()).add(milestone);
        }
        milestonesByHunt.values().forEach(list -> list.sort(Comparator.comparingInt(Milestone::sortKey)));
        plugin.getLogger().info("Loaded " + hunts.size() + " hunt(s) and " + heads.size() + " head(s)");
    }

    public void startScheduler() {
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSchedules, 40L, 20L);
    }

    public void stop() {
        if (scheduleTask != null) scheduleTask.cancel();
    }

    /** Applies any start or end whose time has passed, including ones missed while the server was down. */
    private void tickSchedules() {
        long now = System.currentTimeMillis();
        for (Hunt hunt : List.copyOf(hunts.values())) {
            if (hunt.isArchived()) continue;
            if (!hunt.isActive() && hunt.getStartAt() != null && hunt.getStartAt() <= now) {
                start(hunt, !hunt.isStartContinues() || lastRun(hunt).isEmpty(), "#schedule");
            } else if (hunt.isActive() && hunt.getEndAt() != null && hunt.getEndAt() <= now) {
                end(hunt, "#schedule");
            }
        }
    }

    // ---------------------------------------------------------------- hunts

    public Optional<Hunt> hunt(String id) {
        Hunt hunt = hunts.get(id == null ? null : id.toLowerCase(Locale.ROOT));
        return hunt == null || hunt.isArchived() ? Optional.empty() : Optional.of(hunt);
    }

    /** Includes archived hunts, for history lookups. */
    public Optional<Hunt> huntIncludingArchived(String id) {
        return Optional.ofNullable(hunts.get(id));
    }

    public List<Hunt> hunts() {
        return hunts.values().stream().filter(h -> !h.isArchived()).toList();
    }

    public List<Hunt> activeHunts() {
        return hunts.values().stream().filter(h -> !h.isArchived() && h.isActive()).toList();
    }

    public HuntState state(Hunt hunt) {
        return hunt.state(!runs(hunt).isEmpty());
    }

    public Hunt createHunt(String id, String actor) {
        String name = "<#B8E6D5>" + titleCase(id);
        Hunt hunt = new Hunt(id, name, null, false, null, null, false, false, System.currentTimeMillis());
        hunts.put(id, hunt);
        db.saveHunt(hunt);
        db.audit(actor, id, "create", null);
        return hunt;
    }

    /** Archives a hunt: ends it if running and hides it. Runs, finds and results are kept. */
    public void archiveHunt(Hunt hunt, String actor) {
        if (hunt.isActive()) end(hunt, actor);
        hunt.setStartAt(null);
        hunt.setEndAt(null);
        hunt.setArchived(true);
        db.saveHunt(hunt);
        plugin.getEditorService().closeSessions(hunt);
        db.audit(actor, hunt.getId(), "archive", null);
    }

    public void saveHunt(Hunt hunt) {
        db.saveHunt(hunt);
    }

    public void setSetting(Hunt hunt, HuntSetting setting, String value, String actor) {
        hunt.getSettings().put(setting.key(), value);
        db.saveSetting(hunt.getId(), setting.key(), value);
        db.audit(actor, hunt.getId(), "setting", setting.key() + "=" + value);
    }

    // ---------------------------------------------------------------- start / end / schedule

    /**
     * @param newRun start a fresh run (fresh progress) rather than reopening the last one
     * @return the number of blocked heads once every head chunk has been synced
     */
    public CompletableFuture<Integer> start(Hunt hunt, boolean newRun, String actor) {
        if (hunt.isActive()) return CompletableFuture.completedFuture(0);
        Optional<Run> last = lastRun(hunt);
        Run run;
        if (!newRun && last.isPresent() && !last.get().isImported()) {
            run = last.get();
            run.setEndedAt(null);
        } else {
            run = new Run(db.nextId("runs"), hunt.getId(), runs(hunt).size() + 1, System.currentTimeMillis(), null, false);
            runs.put(run.getId(), run);
            runsByHunt.computeIfAbsent(hunt.getId(), k -> new ArrayList<>()).add(run);
        }
        db.saveRun(run);
        hunt.setActive(true);
        hunt.setStartAt(null);
        hunt.setStartContinues(false);
        db.saveHunt(hunt);
        plugin.getProgressService().openRun(run);
        CompletableFuture<Integer> synced = plugin.getWorldSync().syncHunt(hunt);
        plugin.getEditorService().refreshHunt(hunt);
        db.audit(actor, hunt.getId(), "start", "run " + run.getNumber() + (newRun ? " (new)" : " (continued)"));
        plugin.getMessages().send(Bukkit.getConsoleSender(), "hunt-started-log", Messages.hunt(hunt));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("oakhunt.use")) plugin.getMessages().send(player, "hunt-started", Messages.hunt(hunt));
        }
        return synced;
    }

    public void end(Hunt hunt, String actor) {
        if (!hunt.isActive()) return;
        currentRun(hunt).ifPresent(run -> {
            run.setEndedAt(System.currentTimeMillis());
            db.saveRun(run);
        });
        hunt.setActive(false);
        hunt.setEndAt(null);
        db.saveHunt(hunt);
        for (Head head : heads(hunt)) {
            if (head.isConflict()) {
                head.setConflict(false);
                db.saveHead(head);
            }
        }
        plugin.getWorldSync().syncHunt(hunt);
        plugin.getEditorService().refreshHunt(hunt);
        db.audit(actor, hunt.getId(), "end", null);
        plugin.getMessages().send(Bukkit.getConsoleSender(), "hunt-ended-log", Messages.hunt(hunt));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("oakhunt.use")) plugin.getMessages().send(player, "hunt-ended", Messages.hunt(hunt));
        }
    }

    public void scheduleStart(Hunt hunt, long at, boolean continues, String actor) {
        hunt.setStartAt(at);
        hunt.setStartContinues(continues);
        db.saveHunt(hunt);
        db.audit(actor, hunt.getId(), "schedule-start", String.valueOf(at));
    }

    public void scheduleEnd(Hunt hunt, long at, String actor) {
        hunt.setEndAt(at);
        db.saveHunt(hunt);
        db.audit(actor, hunt.getId(), "schedule-end", String.valueOf(at));
    }

    public void cancelSchedule(Hunt hunt, String actor) {
        hunt.setStartAt(null);
        hunt.setEndAt(null);
        hunt.setStartContinues(false);
        db.saveHunt(hunt);
        db.audit(actor, hunt.getId(), "cancel-schedule", null);
    }

    // ---------------------------------------------------------------- runs

    public List<Run> runs(Hunt hunt) {
        return runsByHunt.getOrDefault(hunt.getId(), List.of());
    }

    public Optional<Run> lastRun(Hunt hunt) {
        List<Run> list = runs(hunt);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
    }

    /** The open run of an active hunt. */
    public Optional<Run> currentRun(Hunt hunt) {
        return lastRun(hunt).filter(Run::isOpen);
    }

    public Optional<Run> run(int id) {
        return Optional.ofNullable(runs.get(id));
    }

    public Collection<Run> allRuns() {
        return runs.values();
    }

    /** Adds a closed run from an import. */
    public Run importRun(Hunt hunt, long startedAt, long endedAt) {
        Run run = new Run(db.nextId("runs"), hunt.getId(), runs(hunt).size() + 1, startedAt, endedAt, true);
        runs.put(run.getId(), run);
        runsByHunt.computeIfAbsent(hunt.getId(), k -> new ArrayList<>()).add(run);
        db.saveRun(run);
        return run;
    }

    // ---------------------------------------------------------------- heads

    /** Non-archived heads of a hunt, by number. */
    public List<Head> heads(Hunt hunt) {
        return headsByHunt.getOrDefault(hunt.getId(), List.of()).stream().filter(h -> !h.isArchived()).toList();
    }

    /** Heads that count toward the total: not archived, not blocked by a conflict. */
    public int liveTotal(Hunt hunt) {
        int total = 0;
        for (Head head : headsByHunt.getOrDefault(hunt.getId(), List.of())) {
            if (head.isLive()) total++;
        }
        return total;
    }

    public Optional<Head> head(int id) {
        Head head = heads.get(id);
        return head == null || head.isArchived() ? Optional.empty() : Optional.of(head);
    }

    /** Any head ever recorded, including archived ones (undo). */
    public Optional<Head> headIncludingArchived(int id) {
        return Optional.ofNullable(heads.get(id));
    }

    public Head headAt(Block block) {
        return headsByBlock.get(BlockKey.of(block));
    }

    public Head headAt(BlockKey key) {
        return headsByBlock.get(key);
    }

    public List<Head> headsInChunk(String world, int chunkX, int chunkZ) {
        Map<Long, List<Head>> byChunk = headsByChunk.get(world);
        if (byChunk == null) return List.of();
        return byChunk.getOrDefault(Head.chunkKey(chunkX, chunkZ), List.of());
    }

    public Head addHead(Hunt hunt, Block block, String blockData, String texture, String actor) {
        int number = headsByHunt.getOrDefault(hunt.getId(), List.of()).stream()
                .mapToInt(Head::getNumber).max().orElse(0) + 1;
        Head head = new Head(db.nextId("heads"), hunt.getId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), blockData, texture, number, null,
                false, false, actor, System.currentTimeMillis());
        index(head);
        db.saveHead(head);
        db.audit(actor, hunt.getId(), "add-head", "#" + number + " " + block.getX() + "," + block.getY() + "," + block.getZ());
        plugin.getWorldSync().reconcile(head);
        plugin.getEditorService().refreshHead(head);
        return head;
    }

    /** Imports a head without touching the world. */
    public Head importHead(Hunt hunt, String world, int x, int y, int z, String blockData, String texture, String clue) {
        int number = headsByHunt.getOrDefault(hunt.getId(), List.of()).size() + 1;
        Head head = new Head(db.nextId("heads"), hunt.getId(), world, x, y, z, blockData, texture, number, clue,
                false, false, "#import", System.currentTimeMillis());
        index(head);
        db.saveHead(head);
        return head;
    }

    public void archiveHead(Head head, String actor) {
        plugin.getWorldSync().removeFromWorld(head);
        head.setArchived(true);
        headsByBlock.remove(BlockKey.of(head), head);
        db.saveHead(head);
        db.audit(actor, head.getHuntId(), "remove-head", "#" + head.getNumber());
        plugin.getEditorService().refreshHead(head);
    }

    /**
     * Brings back an archived head (undo).
     *
     * @return false if its spot now holds another hunt's head
     */
    public boolean restoreHead(Head head, String actor) {
        BlockKey key = BlockKey.of(head);
        Head occupant = headsByBlock.get(key);
        if (occupant != null && occupant != head) return false;
        head.setArchived(false);
        headsByBlock.put(key, head);
        db.saveHead(head);
        db.audit(actor, head.getHuntId(), "restore-head", "#" + head.getNumber());
        plugin.getWorldSync().reconcile(head);
        plugin.getEditorService().refreshHead(head);
        return true;
    }

    public void saveHead(Head head) {
        db.saveHead(head);
    }

    public void setHeadTexture(Head head, String texture, String actor) {
        head.setTexture(texture);
        db.saveHead(head);
        db.audit(actor, head.getHuntId(), "head-skin", "#" + head.getNumber());
        plugin.getWorldSync().refreshSkin(head);
        plugin.getEditorService().refreshHead(head);
    }

    public void setClue(Head head, String clue, String actor) {
        head.setClue(clue == null || clue.isBlank() ? null : clue);
        db.saveHead(head);
        db.audit(actor, head.getHuntId(), "clue", "#" + head.getNumber());
        plugin.getEditorService().refreshHead(head);
    }

    private void index(Head head) {
        heads.put(head.getId(), head);
        List<Head> list = headsByHunt.computeIfAbsent(head.getHuntId(), k -> new ArrayList<>());
        list.add(head);
        list.sort(Comparator.comparingInt(Head::getNumber));
        headsByChunk.computeIfAbsent(head.getWorld(), k -> new HashMap<>())
                .computeIfAbsent(head.chunkKey(), k -> new ArrayList<>()).add(head);
        if (!head.isArchived()) headsByBlock.put(BlockKey.of(head), head);
    }

    // ---------------------------------------------------------------- skins

    public List<Skin> skins(Hunt hunt) {
        return skinsByHunt.getOrDefault(hunt.getId(), List.of()).stream().filter(s -> !s.isArchived()).toList();
    }

    public Optional<Skin> skin(int id) {
        return Optional.ofNullable(skins.get(id)).filter(s -> !s.isArchived());
    }

    /** Adds a skin to the set, or returns the existing one with the same texture. */
    public Skin addSkin(Hunt hunt, String texture, String label, String actor) {
        for (Skin skin : skins(hunt)) {
            if (skin.getTexture().equals(texture)) return skin;
        }
        Skin skin = new Skin(db.nextId("skins"), hunt.getId(), texture, label, false);
        skins.put(skin.getId(), skin);
        skinsByHunt.computeIfAbsent(hunt.getId(), k -> new ArrayList<>()).add(skin);
        db.saveSkin(skin);
        db.audit(actor, hunt.getId(), "add-skin", label);
        return skin;
    }

    public void removeSkin(Skin skin, String actor) {
        skin.setArchived(true);
        db.saveSkin(skin);
        db.audit(actor, skin.getHuntId(), "remove-skin", String.valueOf(skin.getId()));
    }

    /**
     * @param updatePlaced also re-skin every head that uses the old texture
     * @return how many heads were updated
     */
    public int replaceSkin(Skin skin, String texture, boolean updatePlaced, String actor) {
        String old = skin.getTexture();
        skin.setTexture(texture);
        db.saveSkin(skin);
        int updated = 0;
        if (updatePlaced) {
            for (Head head : headsByHunt.getOrDefault(skin.getHuntId(), List.of())) {
                if (!head.isArchived() && head.getTexture().equals(old)) {
                    setHeadTexture(head, texture, actor);
                    updated++;
                }
            }
        }
        db.audit(actor, skin.getHuntId(), "replace-skin", skin.getId() + " (" + updated + " heads)");
        return updated;
    }

    // ---------------------------------------------------------------- milestones

    public List<Milestone> milestones(Hunt hunt) {
        return milestonesByHunt.getOrDefault(hunt.getId(), List.of());
    }

    public Optional<Milestone> milestone(int id) {
        return Optional.ofNullable(milestones.get(id));
    }

    /**
     * @return the new milestone, or empty if one with this threshold already exists
     */
    public Optional<Milestone> addMilestone(Hunt hunt, int threshold, String actor) {
        for (Milestone existing : milestones(hunt)) {
            if (existing.getThreshold() == threshold) return Optional.empty();
        }
        Milestone milestone = new Milestone(db.nextId("milestones"), hunt.getId(), threshold, null, null);
        milestones.put(milestone.getId(), milestone);
        List<Milestone> list = milestonesByHunt.computeIfAbsent(hunt.getId(), k -> new ArrayList<>());
        list.add(milestone);
        list.sort(Comparator.comparingInt(Milestone::sortKey));
        db.saveMilestone(milestone, false);
        db.audit(actor, hunt.getId(), "add-milestone", String.valueOf(threshold));
        return Optional.of(milestone);
    }

    public void removeMilestone(Milestone milestone, String actor) {
        milestones.remove(milestone.getId());
        milestonesByHunt.getOrDefault(milestone.getHuntId(), new ArrayList<>()).remove(milestone);
        db.saveMilestone(milestone, true);
        db.audit(actor, milestone.getHuntId(), "remove-milestone", String.valueOf(milestone.getThreshold()));
    }

    public void saveMilestone(Milestone milestone, String actor) {
        db.saveMilestone(milestone, false);
        db.audit(actor, milestone.getHuntId(), "edit-milestone", String.valueOf(milestone.getThreshold()));
    }

    public Reward addReward(Milestone milestone, RewardType type, String value, int amount, byte[] item, String actor) {
        Reward reward = new Reward(db.nextId("rewards"), milestone.getId(), type, value, Math.max(1, amount), item);
        milestone.getRewards().add(reward);
        db.saveReward(reward, false);
        db.audit(actor, milestone.getHuntId(), "add-reward", type + " " + (value == null ? "item" : value));
        return reward;
    }

    public void removeReward(Milestone milestone, Reward reward, String actor) {
        milestone.getRewards().remove(reward);
        db.saveReward(reward, true);
        db.audit(actor, milestone.getHuntId(), "remove-reward", reward.type() + " " + reward.value());
    }

    // ---------------------------------------------------------------- helpers

    private static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("-")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
