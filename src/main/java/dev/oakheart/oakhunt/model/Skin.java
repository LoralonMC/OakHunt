package dev.oakheart.oakhunt.model;

/**
 * One skin in a hunt's skin set. {@code texture} is the base64 "textures" property value.
 */
public class Skin {

    private final int id;
    private final String huntId;
    private String texture;
    private String label;
    private boolean archived;

    public Skin(int id, String huntId, String texture, String label, boolean archived) {
        this.id = id;
        this.huntId = huntId;
        this.texture = texture;
        this.label = label;
        this.archived = archived;
    }

    public int getId() { return id; }
    public String getHuntId() { return huntId; }
    public String getTexture() { return texture; }
    public void setTexture(String texture) { this.texture = texture; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
