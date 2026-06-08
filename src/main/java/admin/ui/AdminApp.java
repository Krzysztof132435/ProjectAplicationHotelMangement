package admin.ui;

import admin.network.AdminNetworkClient;
import core.network.NetworkConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.util.Optional;

public class AdminApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        String host = NetworkConfig.resolveServerHost(getParameters().getRaw());
        int port = NetworkConfig.resolveServerPort(getParameters().getRaw());

        if (!NetworkConfig.hasServerHostOverride(getParameters().getRaw())) {
            Optional<String> selectedHost = askForServerHost(host);
            if (selectedHost.isEmpty()) {
                primaryStage.close();
                return;
            }
            host = selectedHost.get().trim().isEmpty() ? host : selectedHost.get().trim();
        }

        AdminNetworkClient networkClient = new AdminNetworkClient(host, port);
        AdminLoginController loginController = new AdminLoginController(networkClient);

        Scene scene = new Scene(loginController.getView(), 720, 520);

        try {
            scene.getStylesheets().add(getClass().getResource("/client/ui/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Błąd: Nie znaleziono pliku /client/ui/style.css!");
        }

        primaryStage.setTitle("Panel Administratora - Logowanie");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    private Optional<String> askForServerHost(String defaultHost) {
        TextInputDialog dialog = new TextInputDialog(defaultHost);
        dialog.setTitle("Konfiguracja połączenia");
        dialog.setHeaderText("Adres IP serwera");
        dialog.setContentText("Podaj IP laptopa z uruchomionym serwerem:");
        return dialog.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}