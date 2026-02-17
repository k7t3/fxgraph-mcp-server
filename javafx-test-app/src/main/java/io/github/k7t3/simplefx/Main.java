package io.github.k7t3.simplefx;

import io.github.k7t3.simplefx.controller.TodoController;
import io.github.k7t3.simplefx.view.TodoView;
import io.github.k7t3.simplefx.viewmodel.TodoViewModel;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    private static final String TITLE = "TODO App";
    private static final double WINDOW_WIDTH = 800;
    private static final double WINDOW_HEIGHT = 600;

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

        TodoViewModel viewModel = new TodoViewModel();
        TodoView view = new TodoView();
        new TodoController(view, viewModel);

        StackPane root = new StackPane(view);
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
    }
}