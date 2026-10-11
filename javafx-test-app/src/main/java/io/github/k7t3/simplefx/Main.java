package io.github.k7t3.simplefx;

import io.github.k7t3.simplefx.lab.Workbench;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** MCP と CLI の検査・操作を確認する JavaFX アプリケーション。 */
public class Main extends Application {

    private Workbench workbench;

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        workbench = new Workbench(stage);
        var scene = new Scene(workbench, 1200, 880);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setTitle("FXGraph 機能確認アプリ");
        stage.setMinWidth(1000);
        stage.setMinHeight(780);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (workbench != null) workbench.close();
    }
}
