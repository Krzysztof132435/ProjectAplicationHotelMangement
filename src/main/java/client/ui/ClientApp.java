package client.ui;

import client.network.NetworkClient;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class ClientApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        NetworkClient networkClient = new NetworkClient("localhost", 12345);
        LoginController loginController = new LoginController(networkClient);

        primaryStage.setTitle("Hotel Reservation - Logowanie");
        Scene scene = new Scene(loginController.getRootPane(), 720, 520);

        // Ładowanie CSS z katalogu resources
        URL cssResource = getClass().getResource("/client/ui/style.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("Nie znaleziono pliku stylu: /client/ui/style.css");
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(520);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
