package dev.oakheart.oakhunt.model;

import java.util.UUID;

/**
 * A player's standing in one run. Kept beside the per-head finds so an imported run with no
 * head-level data still has a result to show.
 */
public class PlayerResult {

    private final int runId;
    private final UUID uuid;
    private String name;
    private int found;
    private long lastFoundAt;
    private Long completedAt;

    public PlayerResult(int runId, UUID uuid, String name, int found, long lastFoundAt, Long completedAt) {
        this.runId = runId;
        this.uuid = uuid;
        this.name = name;
        this.found = found;
        this.lastFoundAt = lastFoundAt;
        this.completedAt = completedAt;
    }

    public int getRunId() { return runId; }
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getFound() { return found; }
    public void setFound(int found) { this.found = found; }
    public long getLastFoundAt() { return lastFoundAt; }
    public void setLastFoundAt(long lastFoundAt) { this.lastFoundAt = lastFoundAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}
