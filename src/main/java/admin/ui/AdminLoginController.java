package admin.ui;

import admin.network.AdminNetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class AdminLoginController {
    private final AdminNetworkClient networkClient;
    private final BorderPane rootPane = new BorderPane();
    private final VBox formBox = new VBox();

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField passwordTextField = new TextField();
    private final Button actionButton = new Button();
    private final Label statusLabel = new Label();
    private final Label headerLabel = new Label();

    public AdminLoginController(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        rootPane.setPrefSize(720, 520);
        rootPane.getStyleClass().add("login-container");

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

        Platform.runLater(rootPane::requestFocus);
    }

    private VBox createHeaderBox() {
        VBox headerBox = new VBox();
        headerBox.setSpacing(10);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));

        headerLabel.setText("Zaloguj się");
        headerLabel.getStyleClass().add("header-label");

        Label subtitleLabel = new Label("Panel Administratora - Zarządzanie hotelem");
        subtitleLabel.getStyleClass().add("subtitle-label");

        headerBox.getChildren().addAll(headerLabel, subtitleLabel);
        return headerBox;
    }

    private VBox createFormBox() {
        formBox.setSpacing(15);
        formBox.getStyleClass().add("form-box");
        formBox.setMaxWidth(450);
        formBox.setAlignment(Pos.CENTER);

        usernameField.setPromptText("Nazwa administratora");
        usernameField.getStyleClass().add("text-field");
        usernameField.setPrefHeight(45);
        usernameField.setMaxWidth(Double.MAX_VALUE);

        passwordField.setPromptText("Hasło");
        passwordField.getStyleClass().add("password-field");
        passwordField.setStyle("-fx-padding: 12 45 12 12;");
        passwordField.setPrefHeight(45);
        passwordField.setMaxWidth(Double.MAX_VALUE);

        passwordTextField.setPromptText("Hasło");
        passwordTextField.getStyleClass().add("text-field");
        passwordTextField.setStyle("-fx-padding: 12 45 12 12;");
        passwordTextField.setPrefHeight(45);
        passwordTextField.setMaxWidth(Double.MAX_VALUE);
        passwordTextField.setVisible(false);
        passwordTextField.setManaged(false);

        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());

        Button togglePasswordButton = new Button("👁");
        togglePasswordButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-text-fill: #7f8c8d; -fx-font-size: 16; -fx-padding: 0;");
        togglePasswordButton.setPrefSize(40, 45);
        togglePasswordButton.setFocusTraversable(false);

        togglePasswordButton.setOnAction(e -> {
            if (passwordField.isVisible()) {
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                passwordTextField.setVisible(true);
                passwordTextField.setManaged(true);
                togglePasswordButton.setText("🔒");
            } else {
                passwordTextField.setVisible(false);
                passwordTextField.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                togglePasswordButton.setText("👁");
            }
        });

        StackPane passwordStack = new StackPane(passwordField, passwordTextField, togglePasswordButton);
        StackPane.setAlignment(togglePasswordButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(togglePasswordButton, new Insets(0, 5, 0, 0));

        actionButton.setText("Zaloguj się");
        actionButton.getStyleClass().add("primary-button");
        actionButton.setMaxWidth(Double.MAX_VALUE);
        actionButton.setOnAction(event -> handleActionButtonClick());

        usernameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleActionButtonClick(); });
        passwordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleActionButtonClick(); });
        passwordTextField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleActionButtonClick(); });

        formBox.getChildren().addAll(usernameField, passwordStack, actionButton);
        VBox.setVgrow(usernameField, Priority.NEVER);
        VBox.setVgrow(passwordStack, Priority.NEVER);
        return formBox;
    }

    private VBox createFooterBox() {
        VBox footerBox = new VBox();
        footerBox.setSpacing(10);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setPadding(new Insets(20, 0, 0, 0));

        statusLabel.setText("");
        statusLabel.getStyleClass().add("status-label");

        footerBox.getChildren().add(statusLabel);
        return footerBox;
    }

    private void handleActionButtonClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        statusLabel.getStyleClass().removeAll("success", "error");

        if (username.isEmpty() || password.isEmpty()) {
            setStatusMessage("Nazwa użytkownika i hasło nie mogą być puste!", true);
            return;
        }

        actionButton.setDisable(true);
        setStatusMessage("Przetwarzanie...", false);

        Thread backgroundThread = new Thread(() -> {
            try {
                String response = networkClient.login(username, password);
                Platform.runLater(() -> handleServerResponse(response));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatusMessage("Błąd połączenia: " + e.getMessage(), true);
                    actionButton.setDisable(false);
                });
            }
        }, "AdminAuthThread");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void handleServerResponse(String response) {
        if (response == null) {
            setStatusMessage("Brak odpowiedzi z serwera", true);
            actionButton.setDisable(false);
            return;
        }

        if (response.startsWith("OK")) {
            showLoggedInView();
        } else if (response.startsWith("ERROR")) {
            String errorMessage = response.contains(";") ? response.split(";")[1] : response;
            setStatusMessage("Błąd: " + errorMessage, true);
            actionButton.setDisable(false);
        } else {
            setStatusMessage("Nieznana odpowiedź z serwera", true);
            actionButton.setDisable(false);
        }
    }

    private void setStatusMessage(String message, boolean isError) {
        statusLabel.setText(message);
        if (isError) {
            statusLabel.getStyleClass().add("error");
        } else {
            statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
        }
    }

    private void showLoggedInView() {
        Stage stage = (Stage) actionButton.getScene().getWindow();
        if (stage == null) {
            return;
        }

        AdminDashboard dashboard = new AdminDashboard(networkClient, () -> showLoginView(stage));

        Scene dashboardScene = new Scene(dashboard.getView(), 1200, 800);


        try {
            dashboardScene.getStylesheets().add(getClass().getResource("/client/ui/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Błąd ładowania stylów CSS dla Dashboardu");
        }

        stage.setScene(dashboardScene);
        stage.setTitle("Panel Administratora - System Hotelowy");
        stage.setResizable(true);
        stage.setMaximized(true);
        stage.centerOnScreen();
    }

    private void showLoginView(Stage stage) {
        AdminLoginController loginController = new AdminLoginController(networkClient);
        Scene loginScene = new Scene(loginController.getView(), 720, 520);

        try {
            loginScene.getStylesheets().add(getClass().getResource("/client/ui/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Błąd ładowania stylów CSS dla logowania administratora");
        }

        stage.setMaximized(false);
        stage.setScene(loginScene);
        stage.setTitle("Panel Administratora - Logowanie");
        stage.setResizable(true);
        stage.centerOnScreen();
    }

    public BorderPane getView() {
        return rootPane;
    }

    public BorderPane getRootPane() {
        return getView();
    }
}
