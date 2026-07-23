package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StageInfo {
    private String stageId;
    private String title;
    private double width;
    private double height;
    private double x;
    private double y;
    private boolean focused;
    private int rootNodeId;
    
    public StageInfo() {}
    
    public String getStageId() { return stageId; }
    public void setStageId(String stageId) { this.stageId = stageId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }
    
    public int getRootNodeId() { return rootNodeId; }
    public void setRootNodeId(int rootNodeId) { this.rootNodeId = rootNodeId; }
}
