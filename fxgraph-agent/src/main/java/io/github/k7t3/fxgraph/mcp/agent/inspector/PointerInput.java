package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.util.Map;

/** Validates local pointer offsets and picks rendering nodes without moving the OS pointer. */
final class PointerInput {
    private PointerInput() {}

    static Point2D point(Node node, Map<String, Object> params, String xKey, String yKey, boolean inside) {
        var bounds = node.getBoundsInLocal();
        var x = number(params, xKey);
        var y = number(params, yKey);
        if (inside && (x < 0 || y < 0 || x > bounds.getWidth() || y > bounds.getHeight())) {
            throw new IllegalArgumentException(xKey + "/" + yKey + " must be within the node's local bounds");
        }
        return new Point2D(bounds.getMinX() + x, bounds.getMinY() + y);
    }

    static double number(Map<String, Object> params, String name) {
        if (!(params.get(name) instanceof Number value) || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
        return value.doubleValue();
    }

    static int steps(Map<String, Object> params) {
        if (!params.containsKey("steps")) return 10;
        var value = number(params, "steps");
        if (value != (int) value || value < 1 || value > 120) {
            throw new IllegalArgumentException("steps must be an integer between 1 and 120");
        }
        return (int) value;
    }

    static Node pick(Node node, Point2D scenePoint) {
        if (!node.isVisible() || node.isMouseTransparent() || node.getOpacity() == 0) return null;
        var local = node.sceneToLocal(scenePoint);
        if (local == null || !node.contains(local)) return null;
        var clip = node.getClip();
        if (clip != null && !clip.contains(clip.parentToLocal(local))) return null;
        if (node instanceof Parent parent) {
            var children = new java.util.ArrayList<>(parent.getChildrenUnmodifiable().reversed());
            children.sort(java.util.Comparator.comparingDouble(Node::getViewOrder));
            for (var child : children) {
                var picked = pick(child, scenePoint);
                if (picked != null) return picked;
            }
        }
        return node;
    }
}
