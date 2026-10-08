package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Lightweight scene graph node representation.
 * Optimized for efficient JSON serialization with minimal size.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneGraphNode {
    private int nodeId;
    private Integer parentId;
    private Boolean inScene;
    private Boolean effectiveVisible;
    private Boolean clipped;
    private String visibilityReason;
    private String id;
    private String type;
    private List<SceneGraphNode> children;
    private boolean visible;
    private List<String> styleClass;
    private Map<String, Object> bounds;
    private Double opacity;
    private Double scaleX;
    private Double scaleY;
    private Double rotate;
    private Map<String, Object> properties;

    public SceneGraphNode() {}

    // Getters and Setters
    public int getNodeId() { return nodeId; }
    public void setNodeId(int nodeId) { this.nodeId = nodeId; }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public Boolean getInScene() { return inScene; }
    public void setInScene(Boolean inScene) { this.inScene = inScene; }

    public Boolean getEffectiveVisible() { return effectiveVisible; }
    public void setEffectiveVisible(Boolean effectiveVisible) { this.effectiveVisible = effectiveVisible; }

    public Boolean getClipped() { return clipped; }
    public void setClipped(Boolean clipped) { this.clipped = clipped; }

    public String getVisibilityReason() { return visibilityReason; }
    public void setVisibilityReason(String visibilityReason) { this.visibilityReason = visibilityReason; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<SceneGraphNode> getChildren() { return children; }
    public void setChildren(List<SceneGraphNode> children) { this.children = children; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public List<String> getStyleClass() { return styleClass; }
    public void setStyleClass(List<String> styleClass) { this.styleClass = styleClass; }

    public Map<String, Object> getBounds() { return bounds; }
    public void setBounds(Map<String, Object> bounds) { this.bounds = bounds; }

    public Double getOpacity() { return opacity; }
    public void setOpacity(Double opacity) { this.opacity = opacity; }

    public Double getScaleX() { return scaleX; }
    public void setScaleX(Double scaleX) { this.scaleX = scaleX; }

    public Double getScaleY() { return scaleY; }
    public void setScaleY(Double scaleY) { this.scaleY = scaleY; }

    public Double getRotate() { return rotate; }
    public void setRotate(Double rotate) { this.rotate = rotate; }

    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
