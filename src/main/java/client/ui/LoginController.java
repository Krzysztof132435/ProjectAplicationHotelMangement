package client.ui;

import client.network.NetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class LoginController {
    private final NetworkClient networkClient;
    private final BorderPane rootPane = new BorderPane();
    private final VBox formBox = new VBox();

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmPasswordField = new PasswordField();
    private final Button actionButton = new Button();
    private final Button toggleButton = new Button();
    private final Label statusLabel = new Label();
    private final Label headerLabel = new Label();

    private boolean isLoginMode = true;

    public LoginController(NetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        rootPane.setPrefSize(720, 520);
        rootPane.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 30;");

        VBox headerBox = createHeaderBox();
        rootPane.setTop(headerBox);

        createFormBox();

        VBox contentWrapper = new VBox();
        contentWrapper.setAlignment(Pos.CENTER);
        contentWrapper.setSpacing(20);
        contentWrapper.setPadding(new Insets(10, 0, 0, 0));
        contentWrapper.getChildren().add(formBox);

        rootPane.setCenter(contentWrapper);

        VBox footerBox = createFooterBox();
        rootPane.setBottom(footerBox);
    }

    private VBox createHeaderBox() {
        VBox headerBox = new VBox();
        headerBox.setSpacing(10);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));

        headerLabel.setText("Zaloguj się");
        headerLabel.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label subtitleLabel = new Label("Zarządzaj swoimi rezerwacjami hotelowymi");
        subtitleLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #7f8c8d;");

        headerBox.getChildren().addAll(headerLabel, subtitleLabel);
        return headerBox;
    }

    private VBox createFormBox() {
        formBox.setSpacing(15);
        formBox.setStyle(
                "-fx-background-color: #ffffff; -fx-padding: 32; -fx-border-radius: 16; -fx-background-radius: 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 24, 0, 0, 8);");
        formBox.setMaxWidth(520);
        formBox.setAlignment(Pos.CENTER);

        usernameField.setPromptText("Nazwa użytkownika");
        usernameField.setStyle(
                "-fx-padding: 14; -fx-font-size: 15; -fx-border-radius: 8; -fx-border-color: #dfe3ea; -fx-border-width: 1; -fx-control-inner-background: #f8f9fb; -fx-text-fill: #2c3e50;");
        usernameField.setPrefHeight(50);
        usernameField.setMaxWidth(Double.MAX_VALUE);

        passwordField.setPromptText("Hasło");
        passwordField.setStyle(
                "-fx-padding: 14; -fx-font-size: 15; -fx-border-radius: 8; -fx-border-color: #dfe3ea; -fx-border-width: 1; -fx-control-inner-background: #f8f9fb; -fx-text-fill: #2c3e50;");
        passwordField.setPrefHeight(50);
        passwordField.setMaxWidth(Double.MAX_VALUE);

        confirmPasswordField.setPromptText("Powtórz hasło");
        confirmPasswordField.setStyle(
                "-fx-padding: 14; -fx-font-size: 15; -fx-border-radius: 8; -fx-border-color: #dfe3ea; -fx-border-width: 1; -fx-control-inner-background: #f8f9fb; -fx-text-fill: #2c3e50;");
        confirmPasswordField.setPrefHeight(50);
        confirmPasswordField.setMaxWidth(Double.MAX_VALUE);
        confirmPasswordField.setVisible(false);
        confirmPasswordField.setManaged(false);

        actionButton.setText("Zaloguj się");
        actionButton.setStyle(
                "-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #2c3e50; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;");
        actionButton.setPrefHeight(52);
        actionButton.setMaxWidth(Double.MAX_VALUE);
        actionButton.setOnAction(event -> handleActionButtonClick());

        toggleButton.setText("Nie masz konta? Zarejestruj się");
        toggleButton.setStyle(
                "-fx-font-size: 13; -fx-background-color: transparent; -fx-text-fill: #2c3e50; -fx-border-width: 0; -fx-underline: true; -fx-cursor: hand;");
        toggleButton.setOnAction(event -> toggleMode());

        formBox.getChildren().addAll(usernameField, passwordField, confirmPasswordField, actionButton, toggleButton);
        VBox.setVgrow(usernameField, Priority.NEVER);
        VBox.setVgrow(passwordField, Priority.NEVER);
        VBox.setVgrow(confirmPasswordField, Priority.NEVER);
        return formBox;
    }

    private VBox createFooterBox() {
        VBox footerBox = new VBox();
        footerBox.setSpacing(10);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setPadding(new Insets(20, 0, 0, 0));

        statusLabel.setText("");
        statusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #e74c3c;");

        footerBox.getChildren().add(statusLabel);
        return footerBox;
    }

    private void handleActionButtonClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatusMessage("Nazwa użytkownika i hasło nie mogą być puste!", true);
            return;
        }

        if (!isLoginMode) {
            String confirmPassword = confirmPasswordField.getText().trim();
            if (confirmPassword.isEmpty()) {
                setStatusMessage("Potwierdź hasło, aby dokończyć rejestrację.", true);
                return;
            }
            if (!password.equals(confirmPassword)) {
                setStatusMessage("Hasła muszą być takie same.", true);
                return;
            }
        }

        actionButton.setDisable(true);
        setStatusMessage("Przetwarzanie...", false);

        Thread backgroundThread = new Thread(() -> {
            try {
                String response;
                if (isLoginMode) {
                    response = networkClient.login(username, password);
                } else {
                    response = networkClient.register(username, password);
                }
                Platform.runLater(() -> handleServerResponse(response, username));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatusMessage("Błąd połączenia: " + e.getMessage(), true);
                    actionButton.setDisable(false);
                });
            }
        }, "AuthThread");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void handleServerResponse(String response, String username) {
        if (response == null) {
            setStatusMessage("Brak odpowiedzi z serwera", true);
            actionButton.setDisable(false);
            return;
        }

        if (response.startsWith("OK")) {
            if (!isLoginMode) {
                setStatusMessage("Rejestracja udana! Możesz teraz zalogować się.", false);
                toggleMode();
                clearFields();
            } else {
                showLoggedInView(username);
            }
        } else if (response.startsWith("ERROR")) {
            String errorMessage = response.length() > 6 ? response.substring(6) : response;
            setStatusMessage("Błąd: " + errorMessage, true);
        } else {
            setStatusMessage("Nieznana odpowiedź z serwera", true);
        }
        actionButton.setDisable(false);
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            headerLabel.setText("Zaloguj się");
            actionButton.setText("Zaloguj się");
            toggleButton.setText("Nie masz konta? Zarejestruj się");
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
        } else {
            headerLabel.setText("Zarejestruj się");
            actionButton.setText("Utwórz konto");
            toggleButton.setText("Masz już konto? Zaloguj się");
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
        }
        clearFields();
        clearStatus();
    }

    private void setStatusMessage(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + (isError ? "#e74c3c" : "#27ae60") + ";");
    }

    private void clearFields() {
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private void clearStatus() {
        statusLabel.setText("");
    }

    private void showLoggedInView(String username) {
        MainView mainView = new MainView();

        rootPane.setStyle("-fx-background-color: white; -fx-padding: 0;");
        rootPane.setTop(null);
        rootPane.setBottom(null);
        rootPane.setCenter(mainView.getRootPane());
    }

    public BorderPane getRootPane() {
        return rootPane;
    }
}
