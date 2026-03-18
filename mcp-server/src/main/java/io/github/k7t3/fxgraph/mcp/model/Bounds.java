package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Bounds {
    private double minX;
    private double minY;
    private double width;
    private double height;
    
    public Bounds() {}
    
    public Bounds(double minX, double minY, double width, double height) {
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
    }
    
    public double getMinX() { return minX; }
    public void setMinX(double minX) { this.minX = minX; }
    
    public double getMinY() { return minY; }
    public void setMinY(double minY) { this.minY = minY; }
    
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
}
