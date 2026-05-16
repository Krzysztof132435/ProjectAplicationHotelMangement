package client.ui;

import javafx.scene.layout.BorderPane;

public class MainView {
    private final BorderPane rootPane = new BorderPane();

    public MainView() {
        rootPane.setStyle("-fx-background-color: white;");
        rootPane.setPrefSize(720, 520);
    }

    public BorderPane getRootPane() {
        return rootPane;
    }

    public void setContent(javafx.scene.Node content) {
        rootPane.setCenter(content);
    }
}
