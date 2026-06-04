package client.ui;

import client.network.NetworkClient;
import core.network.NetworkConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;

public class ClientApp extends Application {
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

        NetworkClient networkClient = new NetworkClient(host, port);
        LoginController loginController = new LoginController(networkClient, primaryStage);

        primaryStage.setTitle("Hotel Reservation - Logowanie");
        Scene scene = new Scene(loginController.getRootPane(), 720, 520);


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

    private Optional<String> askForServerHost(String defaultHost) {
        TextInputDialog dialog = new TextInputDialog(defaultHost);
        dialog.setTitle("Konfiguracja polaczenia");
        dialog.setHeaderText("Adres IP serwera");
        dialog.setContentText("Podaj IP laptopa z uruchomionym serwerem:");
        return dialog.showAndWait();
    }
}
