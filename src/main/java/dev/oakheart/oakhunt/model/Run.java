package dev.oakheart.oakhunt.model;

/**
 * One run of a hunt. Finds, results and payouts belong to a run.
 */
public class Run {

    private final int id;
    private final String huntId;
    private final int number;
    private final long startedAt;
    private Long endedAt;
    private final boolean imported;

    public Run(int id, String huntId, int number, long startedAt, Long endedAt, boolean imported) {
        this.id = id;
        this.huntId = huntId;
        this.number = number;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.imported = imported;
    }

    public int getId() { return id; }
    public String getHuntId() { return huntId; }
    public int getNumber() { return number; }
    public long getStartedAt() { return startedAt; }
    public Long getEndedAt() { return endedAt; }
    public void setEndedAt(Long endedAt) { this.endedAt = endedAt; }
    public boolean isImported() { return imported; }
    public boolean isOpen() { return endedAt == null; }
}
