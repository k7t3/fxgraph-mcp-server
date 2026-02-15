package io.github.k7t3.fxgraph.mcp.agent;

import io.github.k7t3.fxgraph.mcp.model.*;
import com.sun.tools.attach.*;
import javax.management.*;
import javax.management.remote.*;
import java.io.*;
import java.util.*;

public class JavaFxAgent {
    
    private VirtualMachine vm;
    private String pid;
    private boolean connected = false;
    private MBeanServerConnection mbeanConnection;
    
    public JavaFxAgent(String pid) {
        this.pid = pid;
    }
    
    public boolean connect() throws Exception {
        try {
            vm = VirtualMachine.attach(pid);
            String connectorAddress = vm.startLocalManagementAgent();
            
            if (connectorAddress == null) {
                // Fallback: try to load management agent manually
                String javaHome = vm.getSystemProperties().getProperty("java.home");
                String agentPath = javaHome + File.separator + "jre" + File.separator + 
                    "lib" + File.separator + "management-agent.jar";
                if (!new File(agentPath).exists()) {
                    agentPath = javaHome + File.separator + "lib" + File.separator + 
                        "management-agent.jar";
                }
                vm.loadAgent(agentPath);
                connectorAddress = vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
            }
            
            JMXServiceURL url = new JMXServiceURL(connectorAddress);
            JMXConnector connector = JMXConnectorFactory.connect(url);
            mbeanConnection = connector.getMBeanServerConnection();
            
            // Check if JavaFX is present
            if (!isJavaFxApplication()) {
                disconnect();
                throw new Exception("Target JVM is not a JavaFX application");
            }
            
            connected = true;
            return true;
            
        } catch (Exception e) {
            disconnect();
            throw new Exception("Failed to connect: " + e.getMessage(), e);
        }
    }
    
    public void disconnect() {
        connected = false;
        if (vm != null) {
            try {
                vm.detach();
            } catch (IOException e) {
                // Ignore
            }
            vm = null;
        }
        mbeanConnection = null;
    }
    
    private boolean isJavaFxApplication() {
        try {
            Set<ObjectName> names = mbeanConnection.queryNames(
                new ObjectName("javafx.application:*"), null);
            return !names.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public List<StageInfo> getStages() throws Exception {
        // This would require an MBean in the target application
        // For now, return empty list - actual implementation needs agent injection
        return new ArrayList<>();
    }
    
    public SVNode getScenegraph(String stageId, int depth, boolean includeProperties) throws Exception {
        // This would require communication with the target application
        // Actual implementation needs agent injection and FX thread access
        return null;
    }
    
    public static List<JavaFxApplication> discoverApplications() {
        List<JavaFxApplication> apps = new ArrayList<>();
        
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : vms) {
            try {
                VirtualMachine vm = VirtualMachine.attach(vmd);
                try {
                    String mainClass = vm.getSystemProperties().getProperty("sun.java.command");
                    String vmName = vm.getSystemProperties().getProperty("java.vm.name");
                    
                    JavaFxApplication app = new JavaFxApplication();
                    app.setPid(Integer.parseInt(vmd.id()));
                    app.setMainClass(mainClass);
                    app.setVmName(vmName);
                    app.setJavaFX(false); // Would need to check via MBean
                    app.setConnected(false);
                    
                    apps.add(app);
                } finally {
                    vm.detach();
                }
            } catch (Exception e) {
                // Skip this VM
            }
        }
        
        return apps;
    }
}
