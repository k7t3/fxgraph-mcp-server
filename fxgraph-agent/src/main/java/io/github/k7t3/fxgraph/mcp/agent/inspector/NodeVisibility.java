package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SubScene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Map;

/** Estimates visibility from scene membership, ancestor state, and bounding-box clipping. */
final class NodeVisibility {
    private NodeVisibility() {}

    record State(boolean inScene, boolean effectiveVisible, boolean clipped, String reason) {
        void addTo(Map<String, Object> result) {
            result.put("inScene", inScene);
            result.put("effectiveVisible", effectiveVisible);
            result.put("clipped", clipped);
            if (reason != null) result.put("visibilityReason", reason);
        }
    }

    static State evaluate(Node node) {
        var scene = node.getScene();
        if (scene == null) return new State(false, false, false, "not in scene");
        var window = scene.getWindow();
        if (window == null || !window.isShowing()) {
            return new State(true, false, false, "window not showing");
        }
        if (window.getOpacity() <= 0) return new State(true, false, false, "window transparent");
        if (window instanceof Stage stage && stage.isIconified()) return new State(true, false, false, "window iconified");
        for (var current = node; current != null; current = NodeHierarchy.parentOf(current)) {
            if (!current.isVisible()) {
                return new State(true, false, false, current == node ? "node invisible" : "ancestor invisible");
            }
            if (current.getOpacity() <= 0) {
                return new State(true, false, false, current == node ? "node transparent" : "ancestor transparent");
            }
        }
        var bounds = node.getBoundsInLocal();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0
                || node instanceof Region region && (region.getWidth() <= 0 || region.getHeight() <= 0)) {
            return new State(true, false, false, "zero-size");
        }
        var visibleBounds = node.localToScreen(bounds);
        if (visibleBounds == null) return new State(true, false, false, "not attached to showing window");
        var original = visibleBounds;
        for (var current = node; current != null; current = NodeHierarchy.parentOf(current)) {
            if (current.getClip() != null) {
                visibleBounds = intersect(visibleBounds, current.localToScreen(current.getClip().getBoundsInParent()));
            }
            if (current instanceof SubScene) {
                visibleBounds = intersect(visibleBounds, current.localToScreen(current.getLayoutBounds()));
            }
            if (visibleBounds == null) return new State(true, false, true, "clipped");
        }
        var viewport = new BoundingBox(window.getX() + scene.getX(), window.getY() + scene.getY(),
                scene.getWidth(), scene.getHeight());
        visibleBounds = intersect(visibleBounds, viewport);
        if (visibleBounds == null) return new State(true, false, true, "outside scene viewport");
        var clipped = visibleBounds.getWidth() < original.getWidth() || visibleBounds.getHeight() < original.getHeight();
        return new State(true, true, clipped, null);
    }

    private static Bounds intersect(Bounds first, Bounds second) {
        if (first == null || second == null) return null;
        var x = Math.max(first.getMinX(), second.getMinX());
        var y = Math.max(first.getMinY(), second.getMinY());
        var width = Math.min(first.getMaxX(), second.getMaxX()) - x;
        var height = Math.min(first.getMaxY(), second.getMaxY()) - y;
        return width > 0 && height > 0 ? new BoundingBox(x, y, width, height) : null;
    }
}
