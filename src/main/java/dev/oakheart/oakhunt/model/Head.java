package dev.oakheart.oakhunt.model;

/**
 * One hidden head: where it sits, how it is rotated, what it looks like.
 */
public class Head {

    private final int id;
    private final String huntId;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private String blockData;
    private String texture;
    private int number;
    private String clue;
    private boolean archived;
    private boolean conflict;
    private final String createdBy;
    private final long createdAt;

    public Head(int id, String huntId, String world, int x, int y, int z, String blockData, String texture,
                int number, String clue, boolean archived, boolean conflict, String createdBy, long createdAt) {
        this.id = id;
        this.huntId = huntId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockData = blockData;
        this.texture = texture;
        this.number = number;
        this.clue = clue;
        this.archived = archived;
        this.conflict = conflict;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getHuntId() { return huntId; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getBlockData() { return blockData; }
    public void setBlockData(String blockData) { this.blockData = blockData; }
    public String getTexture() { return texture; }
    public void setTexture(String texture) { this.texture = texture; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public String getClue() { return clue; }
    public void setClue(String clue) { this.clue = clue; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public boolean isConflict() { return conflict; }
    public void setConflict(boolean conflict) { this.conflict = conflict; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }

    /** Counts toward the hunt's total: not archived and not blocked by a conflict. */
    public boolean isLive() {
        return !archived && !conflict;
    }

    public long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
