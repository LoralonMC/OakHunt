package dev.oakheart.oakhunt.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A hunt definition. Mutable: the in-memory copy is the live one and changes are written through
 * to the database by {@code HuntManager}.
 */
public class Hunt {

    private final String id;
    private String displayName;
    private String description;
    private boolean active;
    private Long startAt;
    private Long endAt;
    private boolean startContinues;
    private boolean archived;
    private final long createdAt;
    private final Map<String, String> settings = new HashMap<>();

    public Hunt(String id, String displayName, String description, boolean active, Long startAt,
                Long endAt, boolean startContinues, boolean archived, long createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.active = active;
        this.startAt = startAt;
        this.endAt = endAt;
        this.startContinues = startContinues;
        this.archived = archived;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getStartAt() { return startAt; }
    public void setStartAt(Long startAt) { this.startAt = startAt; }
    public Long getEndAt() { return endAt; }
    public void setEndAt(Long endAt) { this.endAt = endAt; }
    public boolean isStartContinues() { return startContinues; }
    public void setStartContinues(boolean startContinues) { this.startContinues = startContinues; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public long getCreatedAt() { return createdAt; }
    public Map<String, String> getSettings() { return settings; }

    public boolean setting(HuntSetting setting) {
        return Boolean.parseBoolean(settings.getOrDefault(setting.key(), setting.defaultValue()));
    }

    public int settingInt(HuntSetting setting) {
        try {
            return Integer.parseInt(settings.getOrDefault(setting.key(), setting.defaultValue()));
        } catch (NumberFormatException e) {
            return Integer.parseInt(setting.defaultValue());
        }
    }

    public String settingString(HuntSetting setting) {
        return settings.getOrDefault(setting.key(), setting.defaultValue());
    }

    /**
     * @param hasRun whether the hunt has at least one run on record
     */
    public HuntState state(boolean hasRun) {
        if (active) return HuntState.ACTIVE;
        if (startAt != null) return HuntState.SCHEDULED;
        return hasRun ? HuntState.ENDED : HuntState.DRAFT;
    }
}
