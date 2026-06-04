package admin.ui;

import admin.network.AdminNetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.time.LocalDate;

public class AdminReportsPanel {
    private final AdminNetworkClient networkClient;
    private final VBox root = new VBox(18);

    public AdminReportsPanel(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(40));

        Label titleLabel = new Label("Panel Generowania Raportów");
        titleLabel.getStyleClass().add("header-label");

        Label descriptionLabel = new Label("Wygeneruj raport rezerwacji i przychodu dla wybranego okresu.");
        descriptionLabel.getStyleClass().add("subtitle-label");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(450);

        GridPane formGrid = new GridPane();
        formGrid.setAlignment(Pos.CENTER);
        formGrid.setHgap(12);
        formGrid.setVgap(12);
        formGrid.getStyleClass().add("form-box");

        TextField reportNameField = new TextField("raport_rezerwacji");
        reportNameField.setPromptText("np. raport_maj_2026");
        reportNameField.getStyleClass().add("text-field");
        reportNameField.setPrefWidth(260);

        DatePicker fromDatePicker = new DatePicker(LocalDate.now());
        fromDatePicker.setPrefWidth(260);

        DatePicker toDatePicker = new DatePicker(LocalDate.now().plusDays(1));
        toDatePicker.setPrefWidth(260);

        formGrid.add(new Label("Nazwa raportu:"), 0, 0);
        formGrid.add(reportNameField, 1, 0);
        formGrid.add(new Label("Data od:"), 0, 1);
        formGrid.add(fromDatePicker, 1, 1);
        formGrid.add(new Label("Data do:"), 0, 2);
        formGrid.add(toDatePicker, 1, 2);

        Button reportButton = new Button("Generuj raport rezerwacji");
        reportButton.getStyleClass().add("primary-button");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(500);
        statusLabel.setAlignment(Pos.CENTER);

        reportButton.setOnAction(e -> {
            String reportName = reportNameField.getText().trim();
            LocalDate fromDate = fromDatePicker.getValue();
            LocalDate toDate = toDatePicker.getValue();

            statusLabel.getStyleClass().removeAll("success", "error");
            if (reportName.isEmpty()) {
                statusLabel.setText("Podaj nazwę raportu.");
                statusLabel.getStyleClass().add("error");
                return;
            }
            if (fromDate == null || toDate == null || !toDate.isAfter(fromDate)) {
                statusLabel.setText("Podaj poprawny zakres dat. Data do musi być późniejsza niż data od.");
                statusLabel.getStyleClass().add("error");
                return;
            }

            reportButton.setDisable(true);
            statusLabel.setText("Trwa generowanie raportu przez serwer...");

            new Thread(() -> {
                try {
                    String response = networkClient.generateReport(reportName, fromDate, toDate);
                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        if (response.startsWith("OK")) {
                            String[] parts = response.split(";");
                            String path = parts.length > 2 ? parts[2] : "katalogu głównym serwera";
                            statusLabel.setText("Sukces! Raport został pomyślnie wygenerowany.\nPlik zapisano w: " + path);
                            statusLabel.getStyleClass().add("success");
                        } else {
                            String errorMsg = response.contains(";") ? response.split(";")[1] : response;
                            statusLabel.setText("Błąd generowania: " + errorMsg);
                            statusLabel.getStyleClass().add("error");
                        }
                        reportButton.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        statusLabel.setText("Błąd połączenia z serwerem: " + ex.getMessage());
                        statusLabel.getStyleClass().add("error");
                        reportButton.setDisable(false);
                    });
                }
            }).start();
        });

        root.getChildren().addAll(titleLabel, descriptionLabel, formGrid, reportButton, statusLabel);
    }

    public VBox getRoot() {
        return root;
    }
}