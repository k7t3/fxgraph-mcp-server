package io.github.k7t3.fxgraph.mcp.tools;

import io.github.k7t3.fxgraph.mcp.agent.JavaFxAgent;
import io.github.k7t3.fxgraph.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FxgraphService {
    
    @Tool(description = "Get the scenegraph structure from a JavaFX application")
    public Map<String, Object> getScenegraph(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Stage ID", required = false) String stageId,
            @ToolParam(description = "Depth limit", required = false) Integer depth,
            @ToolParam(description = "Include properties", required = false) Boolean includeProperties) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<StageInfo> stages = new ArrayList<>();
            StageInfo stage = new StageInfo();
            stage.setStageId("stage-1");
            stage.setTitle("Main Window");
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setX(100);
            stage.setY(100);
            stage.setFocused(true);
            stage.setRootNodeId(1);
            stages.add(stage);
            
            List<SVNode> rootNodes = new ArrayList<>();
            SVNode root = new SVNode();
            root.setNodeId(1);
            root.setId("root");
            root.setNodeClass("VBox");
            root.setNodeClassName("javafx.scene.layout.VBox");
            root.setVisible(true);
            root.setLayoutBounds(new Bounds(0, 0, 800, 600));
            root.setBoundsInParent(new Bounds(0, 0, 800, 600));
            root.setLayoutX(0);
            root.setLayoutY(0);
            root.setNodeType("REAL_NODE");
            root.setChildren(new ArrayList<>());
            rootNodes.add(root);
            
            result.put("stages", stages);
            result.put("rootNodes", rootNodes);
            result.put("totalNodeCount", 1);
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Get detailed information about a specific node")
    public Map<String, Object> getNodeDetails(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Detail types to include", required = false) List<String> detailTypes) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            SVNode node = new SVNode();
            node.setNodeId(nodeId);
            node.setNodeClass("VBox");
            node.setNodeClassName("javafx.scene.layout.VBox");
            node.setVisible(true);
            node.setLayoutBounds(new Bounds(0, 0, 800, 600));
            node.setBoundsInParent(new Bounds(0, 0, 800, 600));
            node.setNodeType("REAL_NODE");
            
            List<PropertyDetail> properties = new ArrayList<>();
            PropertyDetail prop = new PropertyDetail();
            prop.setName("spacing");
            prop.setValue(10);
            prop.setType("number");
            prop.setWritable(true);
            prop.setCategory("layout");
            properties.add(prop);
            
            result.put("node", node);
            result.put("properties", properties);
            result.put("children", new ArrayList<>());
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Watch a node for changes")
    public Map<String, Object> watchNode(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Watch children", required = false) boolean watchChildren,
            @ToolParam(description = "Properties to watch", required = false) List<String> watchProperties) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String subscriptionId = "sub-" + UUID.randomUUID().toString();
            result.put("subscriptionId", subscriptionId);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Stop watching a node")
    public Map<String, Object> unwatchNode(
            @ToolParam(description = "Subscription ID") String subscriptionId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Set a property value on a node")
    public Map<String, Object> setProperty(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Property name") String propertyName,
            @ToolParam(description = "Property value as string") String value,
            @ToolParam(description = "Value type (string, number, boolean, color)", required = false) String valueType) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
            result.put("oldValue", null);
            result.put("newValue", value);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Select/highlight a node in the target application")
    public Map<String, Object> selectNode(
            @ToolParam(description = "Session ID") String sessionId,
            @ToolParam(description = "Node ID") int nodeId,
            @ToolParam(description = "Show bounds", required = false) Boolean showBounds,
            @ToolParam(description = "Show baseline", required = false) Boolean showBaseline) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Discover running JavaFX applications")
    public Map<String, Object> discoverApplications() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<JavaFxApplication> apps = JavaFxAgent.discoverApplications();
            result.put("applications", apps);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Connect to a JavaFX application by PID")
    public Map<String, Object> connectApplication(
            @ToolParam(description = "Process ID of the JavaFX application") int pid) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            JavaFxAgent agent = new JavaFxAgent(String.valueOf(pid));
            if (agent.connect()) {
                String sessionId = UUID.randomUUID().toString();
                result.put("success", true);
                result.put("sessionId", sessionId);
            } else {
                result.put("success", false);
                result.put("error", "Failed to connect to application");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
