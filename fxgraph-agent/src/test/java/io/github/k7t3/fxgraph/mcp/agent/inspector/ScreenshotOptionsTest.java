package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class ScreenshotOptionsTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private StackPane root;
    @TempDir Path directory;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 400, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultPreservesLargeWindowResolution() {
        var rectangle = show(2026, 1203);

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", directory.resolve("original.png").toString()));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat((Map<String, Object>) response.getData()).containsEntry("width", 2026)
                .containsEntry("height", 1203).containsEntry("sourceWidth", 2026)
                .containsEntry("sourceHeight", 1203).containsEntry("scaled", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void explicitLimitReportsScalingAndSourceDimensions() {
        var rectangle = show(2000, 1000);

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", directory.resolve("scaled.png").toString(), "maxWidth", 800, "maxHeight", 800));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat((Map<String, Object>) response.getData()).containsEntry("width", 800)
                .containsEntry("height", 400).containsEntry("sourceWidth", 2000)
                .containsEntry("sourceHeight", 1000).containsEntry("scaled", true);
    }

    @Test
    void zeroDisablesRequestedResizeLimits() {
        var rectangle = show(2000, 1000);

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", directory.resolve("zero.png").toString(), "maxWidth", 0, "maxHeight", 0));

        assertThat(response.isSuccess()).as(response.getError()).isTrue();
        assertThat(((Map<?, ?>) response.getData()).get("width")).isEqualTo(2000);
    }

    @Test
    void negativeLimitIsRejectedBeforeFileCreation() {
        var rectangle = show(50, 50);
        var path = directory.resolve("invalid.png");

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", path.toString(), "maxWidth", -1));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("maxWidth", "non-negative");
        assertThat(path).doesNotExist();
    }

    @Test
    void oversizedSourceIsRejectedBeforeSnapshotAllocation() {
        var rectangle = show(10000, 2);
        var path = directory.resolve("oversized.png");

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", path.toString()));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("snapshot safety limit");
        assertThat(path).doesNotExist();
    }

    @Test
    void fractionalBoundsIncludeTheExtraSnapshotPixelInSafetyLimit() throws Exception {
        var rectangle = show(8192, 2);
        WaitForAsyncUtils.asyncFx(() -> rectangle.setTranslateX(0.5)).get(5, TimeUnit.SECONDS);
        var path = directory.resolve("fractional.png");

        var response = inspector.takeScreenshot(Map.of("nodeId", System.identityHashCode(rectangle),
                "savePath", path.toString()));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("snapshot safety limit");
        assertThat(path).doesNotExist();
    }

    private Rectangle show(double width, double height) {
        var rectangle = new Rectangle(width, height);
        try {
            WaitForAsyncUtils.asyncFx(() -> {
                root.getChildren().setAll(rectangle);
                root.applyCss();
                root.layout();
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return rectangle;
    }
}
