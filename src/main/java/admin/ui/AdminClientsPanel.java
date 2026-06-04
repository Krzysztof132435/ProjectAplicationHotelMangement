package admin.ui;

import admin.network.AdminNetworkClient;
import core.model.Client;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

public class AdminClientsPanel {
    private final AdminNetworkClient networkClient;
    private final VBox root = new VBox(15);
    private final ObservableList<Client> clients = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();
    private final Button refreshButton = new Button("Odśwież listę");

    public AdminClientsPanel(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));

        Label titleLabel = new Label("Baza zarejestrowanych klientów");
        titleLabel.getStyleClass().add("header-label");

        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setStyle("-fx-padding: 8 15; -fx-min-height: 35px; -fx-font-size: 13px;");

        TableView<Client> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(500);

        TableColumn<Client, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMaxWidth(80);

        TableColumn<Client, String> usernameCol = new TableColumn<>("Nazwa użytkownika");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        TableColumn<Client, String> createdCol = new TableColumn<>("Data rejestracji");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        statusLabel.getStyleClass().add("status-label");

        TableColumn<Client, Void> actionCol = new TableColumn<>("Akcje");
        actionCol.setPrefWidth(120);
        actionCol.setMaxWidth(120);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button deleteButton = new Button("Usuń");

            {
                deleteButton.getStyleClass().add("secondary-button");
                deleteButton.setOnAction(e -> deleteClientAction(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(deleteButton);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        table.getColumns().addAll(idCol, usernameCol, createdCol, actionCol);
        table.setItems(clients);
        table.setPlaceholder(new Label("Brak klientów w bazie."));

        refreshButton.setOnAction(e -> loadData());

        Platform.runLater(this::loadData);
        root.getChildren().addAll(titleLabel, refreshButton, table, statusLabel);
    }

    private void deleteClientAction(Client client) {
        if (client == null)
            return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Potwierdzenie usunięcia");
        alert.setHeaderText("Czy na pewno chcesz usunąć klienta: " + client.getUsername() + "?");
        alert.setContentText("Tej operacji nie można cofnąć.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.getStyleClass().removeAll("success", "error");
            statusLabel.setText("Usuwanie klienta...");

            new Thread(() -> {
                try {
                    String response = networkClient.deleteClient(client.getId());
                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        if (response.startsWith("OK")) {
                            statusLabel.setText("Klient usunięty pomyślnie.");
                            statusLabel.getStyleClass().add("success");
                            loadData(); // Odśwież tabelę po usunięciu
                        } else {
                            String errorMsg = response.contains(";") ? response.split(";", 2)[1] : response;
                            statusLabel.setText("Błąd: " + errorMsg);
                            statusLabel.getStyleClass().add("error");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        statusLabel.setText("Błąd połączenia: " + ex.getMessage());
                        statusLabel.getStyleClass().add("error");
                    });
                }
            }, "AdminClientDeleteThread").start();
        }
    }

    private void loadData() {
        refreshButton.setDisable(true);
        statusLabel.getStyleClass().removeAll("success", "error");
        statusLabel.setText("Pobieranie bazy klientów...");

        new Thread(() -> {
            try {
                List<Client> result = networkClient.requestAllClients();
                Platform.runLater(() -> {
                    clients.setAll(result);
                    statusLabel.setText("Pobrano " + result.size() + " klientów.");
                    statusLabel.getStyleClass().add("success");
                    refreshButton.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Błąd: " + ex.getMessage());
                    statusLabel.getStyleClass().add("error");
                    refreshButton.setDisable(false);
                });
            }
        }, "AdminClientsLoadThread").start();
    }

    public void refresh() {
        Platform.runLater(this::loadData);
    }

    public VBox getRoot() {
        return root;
    }
}