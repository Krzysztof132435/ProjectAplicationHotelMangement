package admin.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class AdminApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane(new Label("Admin GUI skeleton - add room management here."));
        primaryStage.setTitle("Hotel Reservation Admin");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
