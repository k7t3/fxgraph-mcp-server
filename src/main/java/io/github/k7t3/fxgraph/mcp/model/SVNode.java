package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SVNode {
    private int nodeId;
    private String id;
    private String nodeClass;
    private String nodeClassName;
    private Integer parentId;
    private List<SVNode> children;
    private boolean visible;
    private boolean mouseTransparent;
    private boolean focused;
    private Bounds layoutBounds;
    private Bounds boundsInParent;
    private double layoutX;
    private double layoutY;
    private String style;
    private List<String> styleClass;
    private String nodeType;
    private Map<String, Object> properties;
    
    public SVNode() {}
    
    // Getters and Setters
    public int getNodeId() { return nodeId; }
    public void setNodeId(int nodeId) { this.nodeId = nodeId; }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getNodeClass() { return nodeClass; }
    public void setNodeClass(String nodeClass) { this.nodeClass = nodeClass; }
    
    public String getNodeClassName() { return nodeClassName; }
    public void setNodeClassName(String nodeClassName) { this.nodeClassName = nodeClassName; }
    
    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }
    
    public List<SVNode> getChildren() { return children; }
    public void setChildren(List<SVNode> children) { this.children = children; }
    
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    
    public boolean isMouseTransparent() { return mouseTransparent; }
    public void setMouseTransparent(boolean mouseTransparent) { this.mouseTransparent = mouseTransparent; }
    
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }
    
    public Bounds getLayoutBounds() { return layoutBounds; }
    public void setLayoutBounds(Bounds layoutBounds) { this.layoutBounds = layoutBounds; }
    
    public Bounds getBoundsInParent() { return boundsInParent; }
    public void setBoundsInParent(Bounds boundsInParent) { this.boundsInParent = boundsInParent; }
    
    public double getLayoutX() { return layoutX; }
    public void setLayoutX(double layoutX) { this.layoutX = layoutX; }
    
    public double getLayoutY() { return layoutY; }
    public void setLayoutY(double layoutY) { this.layoutY = layoutY; }
    
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    
    public List<String> getStyleClass() { return styleClass; }
    public void setStyleClass(List<String> styleClass) { this.styleClass = styleClass; }
    
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
