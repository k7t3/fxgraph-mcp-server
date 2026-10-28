package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class PopupWindowTypeTest {
    private final SceneGraphInspector inspector = new SceneGraphInspector();
    private Stage owner;
    private ComboBox<String> combo;

    @Start
    void start(Stage stage) {
        owner = stage;
        combo = new ComboBox<>();
        combo.getItems().addAll("One", "Two");
        stage.setScene(new Scene(new VBox(combo), 400, 240));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void comboPopupUsesDocumentedTypeAndDetailsAgreeWithStages() throws Exception {
        WaitForAsyncUtils.asyncFx(combo::show).get(5, TimeUnit.SECONDS);
        try {
            var response = inspector.getStages();
            assertThat(response.isSuccess()).as(response.getError()).isTrue();
            var windows = (List<Map<String, Object>>) response.getData();
            var popup = windows.stream().filter(info -> String.valueOf(System.identityHashCode(owner))
                    .equals(info.get("ownerWindowId"))).findFirst().orElseThrow();
            assertThat(popup).containsEntry("windowType", "PopupControl");
            var details = inspector.getWindowDetails(Map.of("stageId", popup.get("stageId")));
            assertThat(details.isSuccess()).as(details.getError()).isTrue();
            assertThat((Map<String, Object>) details.getData()).containsEntry("windowType", "PopupControl");
        } finally {
            WaitForAsyncUtils.asyncFx(combo::hide).get(5, TimeUnit.SECONDS);
        }
    }
}
