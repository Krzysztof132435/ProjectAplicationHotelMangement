package admin.ui;

import admin.network.AdminNetworkClient;
import core.model.Reservation;
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

public class AdminReservationsPanel {
    private final AdminNetworkClient networkClient;
    private final VBox root = new VBox(15);
    private final ObservableList<Reservation> reservations = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();
    private final Button refreshButton = new Button("Odśwież");

    public AdminReservationsPanel(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));

        Label titleLabel = new Label("Rezerwacje klientów");
        titleLabel.getStyleClass().add("header-label");

        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setStyle("-fx-padding: 8 15; -fx-min-height: 35px; -fx-font-size: 13px;");

        TableView<Reservation> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(500);

        TableColumn<Reservation, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMaxWidth(60);

        TableColumn<Reservation, String> roomCol = new TableColumn<>("Pokój");
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));

        TableColumn<Reservation, String> guestCol = new TableColumn<>("Klient");
        guestCol.setCellValueFactory(new PropertyValueFactory<>("guestName"));

        TableColumn<Reservation, String> dateRangeCol = new TableColumn<>("Termin");
        dateRangeCol.setCellValueFactory(new PropertyValueFactory<>("dateRangeText"));
        dateRangeCol.setPrefWidth(180);

        TableColumn<Reservation, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<Reservation, String> createdCol = new TableColumn<>("Utworzono");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        createdCol.setPrefWidth(180);

        statusLabel.getStyleClass().add("status-label");

        TableColumn<Reservation, Void> actionCol = new TableColumn<>("Decyzja");
        actionCol.setPrefWidth(210);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button acceptButton = new Button("Akceptuj");
            private final Button rejectButton = new Button("Odrzuć");
            private final HBox buttons = new HBox(8, acceptButton, rejectButton);

            {
                buttons.setAlignment(Pos.CENTER);
                acceptButton.getStyleClass().add("primary-button");
                rejectButton.getStyleClass().add("secondary-button");
                acceptButton.setOnAction(e -> updateReservationFromRow("ACCEPTED"));
                rejectButton.setOnAction(e -> updateReservationFromRow("REJECTED"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Reservation reservation = getTableRow().getItem();
                boolean pending = "PENDING".equalsIgnoreCase(reservation.getStatus());
                acceptButton.setDisable(!pending);
                rejectButton.setDisable(!pending);
                setGraphic(buttons);
            }

            private void updateReservationFromRow(String newStatus) {
                Reservation reservation = getTableRow().getItem();
                if (reservation == null)
                    return;
                acceptButton.setDisable(true);
                rejectButton.setDisable(true);
                statusLabel.getStyleClass().removeAll("success", "error");
                statusLabel.setText("Aktualizowanie rezerwacji #" + reservation.getId() + "...");

                new Thread(() -> {
                    try {
                        String response = networkClient.updateReservationStatus(reservation.getId(), newStatus);
                        Platform.runLater(() -> {
                            statusLabel.getStyleClass().removeAll("success", "error");
                            if (response.startsWith("OK")) {
                                statusLabel.setText("Status rezerwacji zaktualizowany.");
                                statusLabel.getStyleClass().add("success");
                                refreshButton.fire();
                            } else {
                                String errorMsg = response.contains(";") ? response.split(";", 2)[1] : response;
                                statusLabel.setText("Błąd: " + errorMsg);
                                statusLabel.getStyleClass().add("error");
                                acceptButton.setDisable(false);
                                rejectButton.setDisable(false);
                            }
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            statusLabel.getStyleClass().removeAll("success", "error");
                            statusLabel.setText("Błąd połączenia: " + ex.getMessage());
                            statusLabel.getStyleClass().add("error");
                            acceptButton.setDisable(false);
                            rejectButton.setDisable(false);
                        });
                    }
                }, "AdminReservationUpdateThread").start();
            }
        });

        table.getColumns().addAll(idCol, roomCol, guestCol, dateRangeCol, statusCol, createdCol, actionCol);
        table.setItems(reservations);
        table.setPlaceholder(new Label("Brak rezerwacji do wyświetlenia."));

        refreshButton.setOnAction(e -> loadData());

        Platform.runLater(this::loadData);
        root.getChildren().addAll(titleLabel, refreshButton, table, statusLabel);
    }

    private void loadData() {
        refreshButton.setDisable(true);
        statusLabel.getStyleClass().removeAll("success", "error");
        statusLabel.setText("Pobieranie rezerwacji...");

        new Thread(() -> {
            try {
                List<Reservation> result = networkClient.requestAllReservations();
                Platform.runLater(() -> {
                    reservations.setAll(result);
                    statusLabel.setText("Pobrano " + result.size() + " rezerwacji.");
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
        }, "AdminReservationsLoadThread").start();
    }

    public void refresh() {
        Platform.runLater(this::loadData);
    }

    public VBox getRoot() {
        return root;
    }
}