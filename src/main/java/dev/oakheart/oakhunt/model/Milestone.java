package dev.oakheart.oakhunt.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Pays its rewards once per player per run when the player's find count reaches the threshold.
 */
public class Milestone {

    /** Threshold value meaning "every live head in the hunt". */
    public static final int ALL = -1;

    private final int id;
    private final String huntId;
    private final int threshold;
    private String message;
    private String broadcast;
    private final List<Reward> rewards = new ArrayList<>();

    public Milestone(int id, String huntId, int threshold, String message, String broadcast) {
        this.id = id;
        this.huntId = huntId;
        this.threshold = threshold;
        this.message = message;
        this.broadcast = broadcast;
    }

    public int getId() { return id; }
    public String getHuntId() { return huntId; }
    public int getThreshold() { return threshold; }
    public boolean isAll() { return threshold == ALL; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getBroadcast() { return broadcast; }
    public void setBroadcast(String broadcast) { this.broadcast = broadcast; }
    public List<Reward> getRewards() { return rewards; }

    public boolean isReached(int found, int total) {
        return isAll() ? total > 0 && found >= total : found >= threshold;
    }

    /** Sort key: numbered milestones ascending, "all" last. */
    public int sortKey() {
        return isAll() ? Integer.MAX_VALUE : threshold;
    }
}
