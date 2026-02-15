package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyDetail {
    private String name;
    private Object value;
    private String type;
    private boolean writable;
    private String category;
    
    public PropertyDetail() {}
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public boolean isWritable() { return writable; }
    public void setWritable(boolean writable) { this.writable = writable; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
