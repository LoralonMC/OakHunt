package dev.oakheart.oakhunt.service;

import dev.oakheart.oakhunt.OakHunt;
import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.storage.HuntDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Player progress per run: which heads each player found, their result row, and which milestones
 * have paid. Results are kept for every run; finds and payouts only for open runs.
 */
public class ProgressService {

    /** Ranking: most found first, then whoever got there earliest. */
    private static final Comparator<PlayerResult> RANKING = Comparator
            .comparingInt(PlayerResult::getFound).reversed()
            .thenComparingLong(r -> r.getCompletedAt() != null ? r.getCompletedAt() : r.getLastFoundAt());

    private final OakHunt plugin;
    private final HuntDatabase db;
    private final Map<Integer, Map<UUID, PlayerResult>> results = new HashMap<>();
    private final Map<Integer, Map<UUID, Set<Integer>>> finds = new HashMap<>();
    private final Map<Integer, Map<UUID, Set<Integer>>> payouts = new HashMap<>();
    private final Map<Integer, List<PlayerResult>> rankingCache = new HashMap<>();

    public ProgressService(OakHunt plugin, HuntDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() throws SQLException {
        for (PlayerResult result : db.loadResults()) {
            results.computeIfAbsent(result.getRunId(), k -> new HashMap<>()).put(result.getUuid(), result);
        }
        for (Run run : plugin.getHuntManager().allRuns()) {
            if (run.isOpen()) openRun(run);
        }
    }

    /** Loads a run's finds and payouts into memory (a continued run keeps its progress). */
    public void openRun(Run run) {
        if (finds.containsKey(run.getId())) return;
        try {
            finds.put(run.getId(), db.loadFinds(run.getId()));
            payouts.put(run.getId(), db.loadPayouts(run.getId()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load progress for run " + run.getId(), e);
            finds.put(run.getId(), new HashMap<>());
            payouts.put(run.getId(), new HashMap<>());
        }
    }

    /** What a click on a head did. {@code rank} is the completion rank, or 0 if not completed now. */
    public record FindResult(boolean isNew, int found, int total, boolean completedNow, int rank) {
    }

    public FindResult recordFind(UUID uuid, String name, Hunt hunt, Run run, Head head) {
        openRun(run);
        Set<Integer> mine = finds.get(run.getId()).computeIfAbsent(uuid, k -> new HashSet<>());
        int total = plugin.getHuntManager().liveTotal(hunt);
        if (!mine.add(head.getId())) {
            return new FindResult(false, countLive(hunt, mine), total, false, 0);
        }
        long now = System.currentTimeMillis();
        db.insertFind(run.getId(), head.getId(), uuid, now);
        PlayerResult result = results.computeIfAbsent(run.getId(), k -> new HashMap<>())
                .computeIfAbsent(uuid, k -> new PlayerResult(run.getId(), uuid, name, 0, now, null));
        int found = countLive(hunt, mine);
        result.setName(name);
        result.setFound(found);
        result.setLastFoundAt(now);
        boolean completedNow = false;
        if (result.getCompletedAt() == null && total > 0 && found >= total) {
            result.setCompletedAt(now);
            completedNow = true;
        }
        db.saveResult(result);
        rankingCache.remove(run.getId());
        int rank = completedNow ? completions(run) : 0;
        return new FindResult(true, found, total, completedNow, rank);
    }

    /**
     * Marks up to {@code amount} unfound live heads as found (staff grant).
     *
     * @return the new found count
     */
    public int grant(UUID uuid, String name, Hunt hunt, Run run, int amount) {
        int granted = 0;
        for (Head head : plugin.getHuntManager().heads(hunt)) {
            if (granted >= amount) break;
            if (!head.isLive() || hasFound(run, uuid, head)) continue;
            recordFind(uuid, name, hunt, run, head);
            granted++;
        }
        return found(run, uuid, hunt);
    }

    public void reset(UUID uuid, Run run) {
        Map<UUID, Set<Integer>> runFinds = finds.get(run.getId());
        if (runFinds != null) runFinds.remove(uuid);
        Map<UUID, Set<Integer>> runPayouts = payouts.get(run.getId());
        if (runPayouts != null) runPayouts.remove(uuid);
        Map<UUID, PlayerResult> runResults = results.get(run.getId());
        if (runResults != null) runResults.remove(uuid);
        rankingCache.remove(run.getId());
        db.deletePlayerRun(run.getId(), uuid);
    }

    public boolean hasFound(Run run, UUID uuid, Head head) {
        Map<UUID, Set<Integer>> runFinds = finds.get(run.getId());
        return runFinds != null && runFinds.getOrDefault(uuid, Set.of()).contains(head.getId());
    }

    /** Live heads found in an open run (finds of archived or conflicted heads don't count). */
    public int found(Run run, UUID uuid, Hunt hunt) {
        Map<UUID, Set<Integer>> runFinds = finds.get(run.getId());
        if (runFinds == null) {
            PlayerResult result = result(run, uuid);
            return result == null ? 0 : result.getFound();
        }
        return countLive(hunt, runFinds.getOrDefault(uuid, Set.of()));
    }

    public Set<Integer> foundHeads(Run run, UUID uuid) {
        Map<UUID, Set<Integer>> runFinds = finds.get(run.getId());
        return runFinds == null ? Set.of() : runFinds.getOrDefault(uuid, Set.of());
    }

    public PlayerResult result(Run run, UUID uuid) {
        return results.getOrDefault(run.getId(), Map.of()).get(uuid);
    }

    /** Players ranked for a run. Cached until the next find in it. */
    public List<PlayerResult> ranking(Run run) {
        return rankingCache.computeIfAbsent(run.getId(), id -> {
            List<PlayerResult> list = new ArrayList<>(results.getOrDefault(id, Map.of()).values());
            list.removeIf(r -> r.getFound() <= 0);
            list.sort(RANKING);
            return List.copyOf(list);
        });
    }

    /** 1-based rank, or 0 if unranked. */
    public int position(Run run, UUID uuid) {
        List<PlayerResult> ranking = ranking(run);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getUuid().equals(uuid)) return i + 1;
        }
        return 0;
    }

    public int completions(Run run) {
        int count = 0;
        for (PlayerResult result : results.getOrDefault(run.getId(), Map.of()).values()) {
            if (result.getCompletedAt() != null) count++;
        }
        return count;
    }

    /** Completion rank among finishers of a run, or 0 if the player didn't finish. */
    public int completionRank(Run run, UUID uuid) {
        PlayerResult mine = result(run, uuid);
        if (mine == null || mine.getCompletedAt() == null) return 0;
        int rank = 1;
        for (PlayerResult other : results.getOrDefault(run.getId(), Map.of()).values()) {
            if (other.getCompletedAt() != null && other.getCompletedAt() < mine.getCompletedAt()) rank++;
        }
        return rank;
    }

    public int participants(Run run) {
        return ranking(run).size();
    }

    public boolean hasPaid(Run run, UUID uuid, int milestoneId) {
        Map<UUID, Set<Integer>> runPayouts = payouts.get(run.getId());
        return runPayouts != null && runPayouts.getOrDefault(uuid, Set.of()).contains(milestoneId);
    }

    public void markPaid(Run run, UUID uuid, int milestoneId) {
        openRun(run);
        payouts.get(run.getId()).computeIfAbsent(uuid, k -> new HashSet<>()).add(milestoneId);
        db.insertPayout(run.getId(), milestoneId, uuid, System.currentTimeMillis());
    }

    /** Stores an imported result row. */
    public void importResult(PlayerResult result) {
        results.computeIfAbsent(result.getRunId(), k -> new HashMap<>()).put(result.getUuid(), result);
        rankingCache.remove(result.getRunId());
        db.saveResult(result);
    }

    /** Every run the player has a result in, newest first. */
    public List<PlayerResult> history(UUID uuid) {
        List<PlayerResult> out = new ArrayList<>();
        for (Map<UUID, PlayerResult> runResults : results.values()) {
            PlayerResult result = runResults.get(uuid);
            if (result != null && result.getFound() > 0) out.add(result);
        }
        out.sort(Comparator.comparingLong((PlayerResult r) -> plugin.getHuntManager().run(r.getRunId())
                .map(Run::getStartedAt).orElse(0L)).reversed());
        return out;
    }

    private int countLive(Hunt hunt, Set<Integer> headIds) {
        int count = 0;
        for (int id : headIds) {
            Head head = plugin.getHuntManager().head(id).orElse(null);
            if (head != null && head.isLive() && head.getHuntId().equals(hunt.getId())) count++;
        }
        return count;
    }
}
