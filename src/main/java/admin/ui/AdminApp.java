package admin.ui;

import admin.network.AdminNetworkClient;
import core.network.NetworkConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AdminApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        String host = NetworkConfig.resolveServerHost(getParameters().getRaw());
        int port = NetworkConfig.resolveServerPort(getParameters().getRaw());

        AdminNetworkClient networkClient = new AdminNetworkClient(host, port);
        AdminLoginController loginController = new AdminLoginController(networkClient);


        Scene scene = new Scene(loginController.getView(), 720, 520);


        try {
            scene.getStylesheets().add(getClass().getResource("/client/ui/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Błąd: Nie znaleziono pliku /client/ui/style.css w folderze resources!");
        }

        primaryStage.setTitle("Panel Administratora - Logowanie");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
