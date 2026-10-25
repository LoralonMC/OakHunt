package dev.oakheart.oakhunt.model;

/**
 * Per-hunt toggles edited from the hunt's settings menu. Values are stored as strings.
 */
public enum HuntSetting {
    HINTS("hints", "false"),
    TITLE_ON_FIND("title-on-find", "false"),
    COMPLETION_FIREWORK("completion-firework", "true"),
    BROADCAST_FIRST_FINISHER("broadcast-first-finisher", "true"),
    CLUE_MODE("clue-mode", "ALL"),
    CLUE_EVERY("clue-every", "5");

    private final String key;
    private final String defaultValue;

    HuntSetting(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String key() { return key; }
    public String defaultValue() { return defaultValue; }
}
