package client.ui;

import client.network.NetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {
    private final NetworkClient networkClient;
    private final Stage stage;
    private final BorderPane rootPane = new BorderPane();
    private final BorderPane loginScreen = new BorderPane();
    private final VBox formBox = new VBox();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField visiblePasswordField = new TextField();
    private final StackPane passwordPane = new StackPane();
    private final ToggleButton eyeButton = createEyeButton();
    private final PasswordField confirmPasswordField = new PasswordField();
    private final TextField visibleConfirmPasswordField = new TextField();
    private final StackPane confirmPasswordPane = new StackPane();
    private final ToggleButton confirmEyeButton = createEyeButton();
    private final TextField emailField = new TextField();
    private final Button actionButton = new Button();
    private final Button toggleButton = new Button();
    private final Label statusLabel = new Label();
    private final Label headerLabel = new Label();
    private final Hyperlink forgotPasswordLink = new Hyperlink("Zapomniałeś hasła?");

    private boolean isLoginMode = true;

    public LoginController(NetworkClient networkClient, Stage stage) {
        this.networkClient = networkClient;
        this.stage = stage;
        configureView();
    }

    private void configureView() {
        rootPane.setPrefSize(720, 520);
        rootPane.getStyleClass().add("login-container");

        headerLabel.setText("Zaloguj się");
        headerLabel.getStyleClass().add("header-label");

        usernameField.setPromptText("Nazwa użytkownika");
        usernameField.getStyleClass().add("text-field");

        String paddedStyle = "-fx-padding: 12 40 12 12;";

        passwordField.setPromptText("Hasło");
        passwordField.getStyleClass().add("password-field");
        passwordField.setStyle(paddedStyle);

        visiblePasswordField.setPromptText("Hasło");
        visiblePasswordField.getStyleClass().add("text-field");
        visiblePasswordField.setStyle(paddedStyle);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());

        passwordPane.getChildren().addAll(passwordField, visiblePasswordField, eyeButton);

        confirmPasswordField.setPromptText("Potwierdź hasło");
        confirmPasswordField.getStyleClass().add("password-field");
        confirmPasswordField.setStyle(paddedStyle);

        visibleConfirmPasswordField.setPromptText("Potwierdź hasło");
        visibleConfirmPasswordField.getStyleClass().add("text-field");
        visibleConfirmPasswordField.setStyle(paddedStyle);
        visibleConfirmPasswordField.setVisible(false);
        visibleConfirmPasswordField.setManaged(false);
        visibleConfirmPasswordField.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        confirmPasswordPane.getChildren().addAll(confirmPasswordField, visibleConfirmPasswordField, confirmEyeButton);
        confirmPasswordPane.setVisible(false);
        confirmPasswordPane.setManaged(false);

        emailField.setPromptText("Email");
        emailField.getStyleClass().add("text-field");
        emailField.setVisible(false);
        emailField.setManaged(false);

        confirmEyeButton.selectedProperty().bindBidirectional(eyeButton.selectedProperty());
        eyeButton.selectedProperty().addListener((obs, oldVal, isSelected) -> {
            String activeStyle = "-fx-background-color: transparent; -fx-text-fill: #3498db; -fx-font-size: 16px; -fx-cursor: hand;";
            String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #95a5a6; -fx-font-size: 16px; -fx-cursor: hand;";
            eyeButton.setStyle(isSelected ? activeStyle : inactiveStyle);
            confirmEyeButton.setStyle(isSelected ? activeStyle : inactiveStyle);
            togglePasswordVisibility(isSelected);
        });

        actionButton.setText("Zaloguj się");
        actionButton.getStyleClass().add("primary-button");
        actionButton.setMaxWidth(Double.MAX_VALUE);

        toggleButton.setText("Nie masz konta? Zarejestruj się");
        toggleButton.getStyleClass().add("secondary-button");
        toggleButton.setMaxWidth(Double.MAX_VALUE);

        forgotPasswordLink.setStyle("-fx-font-size: 12; -fx-text-fill: #3498db; -fx-cursor: hand;");
        forgotPasswordLink.setOnAction(e -> handleForgotPassword());

        statusLabel.getStyleClass().add("status-label");

        // --- OBSŁUGA KLAWISZA ENTER ---
        usernameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) actionButton.fire(); });
        passwordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) actionButton.fire(); });
        visiblePasswordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) actionButton.fire(); });
        confirmPasswordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) actionButton.fire(); });
        visibleConfirmPasswordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) actionButton.fire(); });

        actionButton.setOnAction(e -> handleActionButtonClick());
        toggleButton.setOnAction(e -> toggleMode());

        formBox.setAlignment(Pos.CENTER);
        formBox.setSpacing(15);
        formBox.setPadding(new Insets(30));
        formBox.setMaxWidth(380);
        formBox.getStyleClass().add("form-box");

        formBox.getChildren().addAll(
                headerLabel,
                usernameField,
                passwordPane,
                confirmPasswordPane,
                emailField,
                actionButton,
                forgotPasswordLink,
                toggleButton,
                statusLabel
        );

        loginScreen.setCenter(formBox);
        rootPane.setCenter(loginScreen);
    }

    private ToggleButton createEyeButton() {
        ToggleButton btn = new ToggleButton("👁");
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #95a5a6; -fx-font-size: 16px; -fx-cursor: hand;");
        btn.setFocusTraversable(false); // Aby tabulator omijał to pole (jak w prawdziwych formularzach)
        StackPane.setAlignment(btn, Pos.CENTER_RIGHT);
        StackPane.setMargin(btn, new Insets(0, 8, 0, 0));
        return btn;
    }

    private void togglePasswordVisibility(boolean show) {
        visiblePasswordField.setVisible(show);
        visiblePasswordField.setManaged(show);
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);

        visibleConfirmPasswordField.setVisible(show);
        visibleConfirmPasswordField.setManaged(show);
        confirmPasswordField.setVisible(!show);
        confirmPasswordField.setManaged(!show);
    }

    private void handleActionButtonClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatusMessage("Wypełnij wszystkie pola!", true);
            return;
        }

        if (!isLoginMode) {
            String confirmPassword = confirmPasswordField.getText().trim();
            String email = emailField.getText().trim();
            if (confirmPassword.isEmpty() || email.isEmpty()) {
                setStatusMessage("Potwierdź hasło i podaj email!", true);
                return;
            }
            if (!password.equals(confirmPassword)) {
                setStatusMessage("Hasła nie są identyczne!", true);
                return;
            }
            if (!email.contains("@")) {
                setStatusMessage("Podaj prawidłowy email!", true);
                return;
            }
        }

        actionButton.setDisable(true);
        setStatusMessage(isLoginMode ? "Logowanie..." : "Rejestracja...", false);

        new Thread(() -> {
            try {
                if (isLoginMode) {
                    String response = networkClient.login(username, password);
                    Platform.runLater(() -> {
                        if (response.startsWith("OK")) {
                            showLoggedInView(username);
                        } else {
                            String msg = response.contains(";") ? response.split(";")[1] : response;
                            setStatusMessage("Błąd: " + msg, true);
                            actionButton.setDisable(false);
                        }
                    });
                } else {
                    String email = emailField.getText().trim();
                    String response = networkClient.register(username, password, email);
                    Platform.runLater(() -> {
                        if (response.startsWith("OK")) {
                            setStatusMessage("Rejestracja pomyślna! Sprawdź email w celu potwierdzenia konta.", false);
                            toggleMode();
                        } else {
                            String msg = response.contains(";") ? response.split(";")[1] : response;
                            setStatusMessage("Błąd: " + msg, true);
                        }
                        actionButton.setDisable(false);
                    });
                }
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    setStatusMessage("Błąd połączenia z serwerem.", true);
                    actionButton.setDisable(false);
                });
            }
        }, "ClientAuthThread").start();
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            headerLabel.setText("Zaloguj się");
            actionButton.setText("Zaloguj się");
            toggleButton.setText("Nie masz konta? Zarejestruj się");
            forgotPasswordLink.setVisible(true);
            forgotPasswordLink.setManaged(true);
            confirmPasswordPane.setVisible(false);
            confirmPasswordPane.setManaged(false);
            emailField.setVisible(false);
            emailField.setManaged(false);
        } else {
            headerLabel.setText("Zarejestruj się");
            actionButton.setText("Zarejestruj się");
            toggleButton.setText("Masz już konto? Zaloguj się");
            forgotPasswordLink.setVisible(false);
            forgotPasswordLink.setManaged(false);
            confirmPasswordPane.setVisible(true);
            confirmPasswordPane.setManaged(true);
            emailField.setVisible(true);
            emailField.setManaged(true);
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
        emailField.clear();
        eyeButton.setSelected(false);
    }

    private void clearStatus() {
        statusLabel.setText("");
    }

    private void handleForgotPassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Resetowanie hasła");
        dialog.setHeaderText("Podaj nazwę użytkownika (wyślemy kod na email)");

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Nazwa użytkownika");

        VBox dialogContent = new VBox(10);
        dialogContent.setPadding(new Insets(20));
        dialogContent.getChildren().add(usernameInput);

        dialog.getDialogPane().setContent(dialogContent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? usernameInput.getText().trim() : null);

        dialog.showAndWait().ifPresent(username -> {
            if (!username.isEmpty()) {
                requestPasswordReset(username);
            }
        });
    }

    private void requestPasswordReset(String username) {
        actionButton.setDisable(true);
        setStatusMessage("Wysyłanie kodu resetującego...", false);

        new Thread(() -> {
            try {
                String response = networkClient.requestPasswordReset(username);
                Platform.runLater(() -> {
                    if (response.startsWith("OK")) {
                        setStatusMessage("Sprawdź email — wysłaliśmy kod resetujący hasło.", false);
                        showPasswordResetDialog();
                    } else {
                        String msg = response.contains(";") ? response.split(";")[1] : response;
                        setStatusMessage("Błąd: " + msg, true);
                    }
                    actionButton.setDisable(false);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    setStatusMessage("Błąd połączenia z serwerem.", true);
                    actionButton.setDisable(false);
                });
            }
        }, "PasswordResetThread").start();
    }

    private void showPasswordResetDialog() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Resetowanie hasła");
        info.setHeaderText("Sprawdź swoją skrzynkę email");
        info.setContentText("Otrzymasz wiadomość z kodem resetującym. Wklej kod poniżej i ustaw nowe hasło.");
        info.showAndWait();

        Dialog<ResetPasswordData> dialog = new Dialog<>();
        dialog.setTitle("Ustaw nowe hasło");
        dialog.setHeaderText("Wklej kod z emaila i ustaw nowe hasło");

        TextField tokenField = new TextField();
        tokenField.setPromptText("Kod resetujący");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Nowe hasło");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Powtórz nowe hasło");

        VBox content = new VBox(10, tokenField, newPasswordField, confirmField);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        ButtonType changeBtn = new ButtonType("Zmień hasło", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != changeBtn) return null;
            return new ResetPasswordData(
                    tokenField.getText().trim(),
                    newPasswordField.getText(),
                    confirmField.getText()
            );
        });

        dialog.showAndWait().ifPresent(data -> {
            if (data.token.isEmpty() || data.newPassword.isEmpty() || data.confirmPassword.isEmpty()) {
                setStatusMessage("Wypełnij wszystkie pola resetowania hasła.", true);
                return;
            }
            if (!data.newPassword.equals(data.confirmPassword)) {
                setStatusMessage("Nowe hasła nie są identyczne.", true);
                return;
            }
            doResetPassword(data.token, data.newPassword);
        });
    }

    private void doResetPassword(String token, String newPassword) {
        actionButton.setDisable(true);
        setStatusMessage("Zmiana hasła...", false);

        new Thread(() -> {
            try {
                String response = networkClient.resetPassword(token, newPassword);
                Platform.runLater(() -> {
                    if (response.startsWith("OK")) {
                        setStatusMessage("Hasło zostało zmienione. Możesz się zalogować.", false);
                    } else {
                        String msg = response.contains(";") ? response.split(";")[1] : response;
                        setStatusMessage("Błąd: " + msg, true);
                    }
                    actionButton.setDisable(false);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    setStatusMessage("Błąd połączenia z serwerem.", true);
                    actionButton.setDisable(false);
                });
            }
        }, "ResetPasswordThread").start();
    }

    private static class ResetPasswordData {
        final String token;
        final String newPassword;
        final String confirmPassword;

        private ResetPasswordData(String token, String newPassword, String confirmPassword) {
            this.token = token;
            this.newPassword = newPassword;
            this.confirmPassword = confirmPassword;
        }
    }

    private void showLoggedInView(String username) {
        ClientDashboard dashboard = new ClientDashboard(networkClient, username, stage);
        dashboard.setOnLogout(this::showLoginView);

        rootPane.setStyle("-fx-background-color: white; -fx-padding: 0;");
        rootPane.setTop(null);
        rootPane.setBottom(null);
        rootPane.setCenter(dashboard.getRootPane());
        stage.setWidth(1000);
        stage.setHeight(700);
    }

    private void showLoginView() {
        rootPane.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 30;");
        rootPane.setCenter(loginScreen);
        clearFields();
        clearStatus();
        actionButton.setDisable(false);
        toggleButton.setDisable(false);
        forgotPasswordLink.setVisible(true);
        forgotPasswordLink.setManaged(true);
        isLoginMode = true;
        headerLabel.setText("Zaloguj się");
        actionButton.setText("Zaloguj się");
        toggleButton.setText("Nie masz konta? Zarejestruj się");
        confirmPasswordPane.setVisible(false);
        confirmPasswordPane.setManaged(false);
        emailField.setVisible(false);
        emailField.setManaged(false);
        stage.setTitle("Hotel Reservation - Logowanie");
    }

    public BorderPane getRootPane() {
        return rootPane;
    }
}
