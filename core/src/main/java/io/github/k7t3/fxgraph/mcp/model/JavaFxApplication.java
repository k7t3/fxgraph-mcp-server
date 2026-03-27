package io.github.k7t3.fxgraph.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JavaFxApplication {
    private int pid;
    private String mainClass;
    private String vmName;
    private boolean isJavaFX;
    private boolean connected;
    
    public JavaFxApplication() {}
    
    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }
    
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    
    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }
    
    public boolean isJavaFX() { return isJavaFX; }
    public void setJavaFX(boolean javaFX) { isJavaFX = javaFX; }
    
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
}
