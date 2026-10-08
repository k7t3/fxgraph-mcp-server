package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;

/** Window operations use public JavaFX APIs and must run on the FX thread. */
final class WindowOperations {
    private WindowOperations() {}

    static Window requireWindow(Map<String, Object> params) {
        var id = params == null ? null : params.get("stageId");
        if (!(id instanceof String stageId) || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId is required");
        }
        return Window.getWindows().stream()
                .filter(window -> stageId.equals(String.valueOf(System.identityHashCode(window))))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Window not found: " + stageId));
    }

    static Map<String, Object> details(Window window) {
        var result = new LinkedHashMap<String, Object>();
        result.put("stageId", String.valueOf(System.identityHashCode(window)));
        result.put("windowType", window.getClass().getSimpleName());
        result.put("x", window.getX());
        result.put("y", window.getY());
        result.put("width", window.getWidth());
        result.put("height", window.getHeight());
        result.put("opacity", window.getOpacity());
        result.put("focused", window.isFocused());
        result.put("showing", window.isShowing());
        if (window.getScene() != null && window.getScene().getRoot() != null) {
            result.put("rootNodeId", System.identityHashCode(window.getScene().getRoot()));
        }
        if (window instanceof Stage stage) {
            result.put("title", stage.getTitle());
            result.put("maximized", stage.isMaximized());
            result.put("iconified", stage.isIconified());
            result.put("alwaysOnTop", stage.isAlwaysOnTop());
            result.put("resizable", stage.isResizable());
        }
        if (window instanceof PopupWindow popup && popup.getOwnerWindow() != null) {
            result.put("ownerWindowId", String.valueOf(System.identityHashCode(popup.getOwnerWindow())));
        }
        return result;
    }

    static Map<String, Object> setProperty(Map<String, Object> params) {
        var window = requireWindow(params);
        if (!(window instanceof Stage stage)) throw new IllegalArgumentException("Window properties require a Stage");
        var property = params.get("propertyName");
        if (!(property instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("propertyName is required");
        }
        var type = switch (name) {
            case "x", "y", "width", "height", "opacity" -> "number";
            case "maximized", "iconified", "alwaysOnTop", "resizable" -> "boolean";
            case "title" -> "string";
            default -> throw new IllegalArgumentException("Unsupported window property: " + name);
        };
        if (params.get("valueType") != null && !type.equals(params.get("valueType"))) {
            throw new IllegalArgumentException(name + " requires type " + type);
        }
        var value = params.get("value");
        if (value == null) throw new IllegalArgumentException("value is required");
        var converted = switch (type) {
            case "number" -> finiteNumber(value, name);
            case "boolean" -> strictBoolean(value);
            default -> value.toString();
        };
        var before = details(stage).get(name);
        var after = switch (name) {
            case "x" -> { stage.setX((Double) converted); yield stage.getX(); }
            case "y" -> { stage.setY((Double) converted); yield stage.getY(); }
            case "width" -> { stage.setWidth((Double) converted); yield stage.getWidth(); }
            case "height" -> { stage.setHeight((Double) converted); yield stage.getHeight(); }
            case "opacity" -> { stage.setOpacity((Double) converted); yield stage.getOpacity(); }
            case "maximized" -> { stage.setMaximized((Boolean) converted); yield stage.isMaximized(); }
            case "iconified" -> { stage.setIconified((Boolean) converted); yield stage.isIconified(); }
            case "alwaysOnTop" -> { stage.setAlwaysOnTop((Boolean) converted); yield stage.isAlwaysOnTop(); }
            case "resizable" -> { stage.setResizable((Boolean) converted); yield stage.isResizable(); }
            case "title" -> { stage.setTitle((String) converted); yield stage.getTitle(); }
            default -> throw new IllegalArgumentException("Unsupported window property: " + name);
        };
        var result = new LinkedHashMap<String, Object>();
        result.put("stageId", String.valueOf(System.identityHashCode(stage)));
        result.put("propertyName", name);
        result.put("oldValue", before);
        result.put("newValue", after);
        return result;
    }

    private static double finiteNumber(Object value, String name) {
        final double number;
        try {
            number = Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " requires a finite number");
        }
        if (!Double.isFinite(number)) throw new IllegalArgumentException(name + " requires a finite number");
        if ((name.equals("width") || name.equals("height")) && number <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        if (name.equals("opacity") && (number < 0 || number > 1)) {
            throw new IllegalArgumentException("opacity must be between 0 and 1");
        }
        return number;
    }

    private static boolean strictBoolean(Object value) {
        if ("true".equalsIgnoreCase(value.toString())) return true;
        if ("false".equalsIgnoreCase(value.toString())) return false;
        throw new IllegalArgumentException("Boolean value must be true or false");
    }
}
