package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Uses public control APIs; Flowless remains an optional dependency of the target application. */
final class ScrollOperations {
    private ScrollOperations() {}

    static Map<String, Object> scroll(Node node, Map<String, Object> params) {
        var dx = delta(params, "dx");
        var dy = delta(params, "dy");
        var align = params.get("align");
        if (align != null && (params.containsKey("dx") || params.containsKey("dy"))) {
            throw new IllegalArgumentException("align cannot be combined with dx or dy");
        }
        if (align == null && !params.containsKey("dx") && !params.containsKey("dy")) {
            throw new IllegalArgumentException("dx, dy or align is required");
        }
        if (align != null && !(align instanceof String edge && java.util.Set.of("top", "bottom", "left", "right").contains(edge))) {
            throw new IllegalArgumentException("align must be top, bottom, left or right");
        }
        layout(node);
        var target = unwrap(node);
        if (target instanceof ScrollPane pane) {
            scrollPane(pane, dx, dy, (String) align);
        } else if (isFlowless(target)) {
            if (align == null) {
                call(target, "scrollXBy", double.class, dx);
                call(target, "scrollYBy", double.class, dy);
            } else {
                var horizontal = align.equals("left") || align.equals("right");
                var last = align.equals("bottom") || align.equals("right");
                call(target, horizontal ? "scrollXToPixel" : "scrollYToPixel", double.class, last ? Double.MAX_VALUE : 0.0);
            }
        } else {
            var flow = standardFlow(target);
            if (align != null) {
                var verticalEdge = align.equals("top") || align.equals("bottom");
                if (verticalEdge != flow.isVertical()) throw new IllegalArgumentException("align does not match the flow orientation");
                flow.setPosition(align.equals("bottom") || align.equals("right") ? 1 : 0);
            } else {
                flow.scrollPixels(flow.isVertical() ? dy : dx);
                var crossAxis = flow.isVertical() ? dx : dy;
                if (crossAxis != 0) fireScroll(flow, flow.isVertical() ? dx : 0, flow.isVertical() ? 0 : dy);
            }
        }
        layout(node);
        return Map.of("nodeId", System.identityHashCode(node), "scrolled", true, "refreshRequired", true);
    }

    static Map<String, Object> scrollToIndex(Node node, Map<String, Object> params) {
        var value = params.get("index");
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 0) {
            throw new IllegalArgumentException("index must be a non-negative integer");
        }
        var index = number.intValue();
        layout(node);
        var target = unwrap(node);
        if (target instanceof ListView<?> list) {
            checkIndex(index, list.getItems().size());
            list.scrollTo(index);
        } else if (target instanceof TableView<?> table) {
            checkIndex(index, table.getItems().size());
            table.scrollTo(index);
        } else if (target instanceof VirtualFlow<?> flow) {
            checkIndex(index, flow.getCellCount());
            flow.scrollTo(index);
        } else if (isFlowless(target)) {
            // getCell validates against the item list without accessing Flowless private fields.
            call(target, "getCell", int.class, index);
            call(target, "show", int.class, index);
        } else {
            throw unsupported(target);
        }
        layout(node);
        return Map.of("nodeId", System.identityHashCode(node), "scrolled", true, "index", index, "refreshRequired", true);
    }

    private static void scrollPane(ScrollPane pane, double dx, double dy, String align) {
        var content = pane.getContent();
        if (content == null) throw new IllegalArgumentException("ScrollPane has no content");
        if (align != null) {
            switch (align) {
                case "top" -> pane.setVvalue(pane.getVmin());
                case "bottom" -> pane.setVvalue(pane.getVmax());
                case "left" -> pane.setHvalue(pane.getHmin());
                case "right" -> pane.setHvalue(pane.getHmax());
                default -> throw new IllegalArgumentException("Unsupported align: " + align);
            }
        } else {
            pane.setHvalue(offset(pane.getHvalue(), dx, content.getBoundsInParent().getWidth() - pane.getViewportBounds().getWidth(),
                    pane.getHmin(), pane.getHmax()));
            pane.setVvalue(offset(pane.getVvalue(), dy, content.getBoundsInParent().getHeight() - pane.getViewportBounds().getHeight(),
                    pane.getVmin(), pane.getVmax()));
        }
    }

    private static double offset(double value, double delta, double pixels, double min, double max) {
        return pixels > 0 ? Math.max(min, Math.min(max, value + delta / pixels * (max - min))) : min;
    }

    private static double delta(Map<String, Object> params, String name) {
        if (!params.containsKey(name)) return 0;
        if (!(params.get(name) instanceof Number value) || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
        return value.doubleValue();
    }

    private static void checkIndex(int index, int size) {
        if (index >= size) throw new IllegalArgumentException("index out of range: " + index + " (item count: " + size + ")");
    }

    private static Node unwrap(Node node) {
        if (hasType(node, "org.fxmisc.flowless.VirtualizedScrollPane")) {
            var content = call(node, "getContent");
            if (content instanceof Node child) return child;
            throw unsupported(node);
        }
        return node;
    }

    private static boolean isFlowless(Node node) {
        return hasType(node, "org.fxmisc.flowless.VirtualFlow");
    }

    private static boolean hasType(Node node, String name) {
        for (Class<?> type = node.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    private static VirtualFlow<?> standardFlow(Node target) {
        if (target instanceof VirtualFlow<?> flow) return flow;
        if ((target instanceof ListView<?> || target instanceof TableView<?>) && target.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            return flow;
        }
        throw unsupported(target);
    }

    private static IllegalArgumentException unsupported(Node target) {
        return new IllegalArgumentException("Unsupported scroll target: " + target.getClass().getName());
    }

    private static Object call(Node target, String name, Class<?> parameterType, Object value) {
        return call(target, name, new Class<?>[]{parameterType}, new Object[]{value});
    }

    private static Object call(Node target, String name) {
        return call(target, name, new Class<?>[0], new Object[0]);
    }

    private static Object call(Node target, String name, Class<?>[] types, Object[] values) {
        try {
            return target.getClass().getMethod(name, types).invoke(target, values);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(name + " failed: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Public scroll API unavailable: " + name, e);
        }
    }

    private static void layout(Node node) {
        if (node.getScene() != null) {
            node.getScene().getRoot().applyCss();
            node.getScene().getRoot().layout();
        }
        if (node instanceof Parent parent) parent.layout();
    }

    private static void fireScroll(Node node, double dx, double dy) {
        var point = node.localToScene(node.getBoundsInLocal().getCenterX(), node.getBoundsInLocal().getCenterY());
        var screen = node.localToScreen(node.getBoundsInLocal().getCenterX(), node.getBoundsInLocal().getCenterY());
        node.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, point.getX(), point.getY(), screen.getX(), screen.getY(),
                false, false, false, false, false, false, -dx, -dy, -dx, -dy,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, new PickResult(node, point.getX(), point.getY())));
    }
}
