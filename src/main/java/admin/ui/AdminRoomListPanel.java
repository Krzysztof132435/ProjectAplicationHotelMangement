package admin.ui;

import admin.network.AdminNetworkClient;
import core.event.RoomEvent;
import core.model.Room;
import core.model.RoomImage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminRoomListPanel {
    private final AdminNetworkClient networkClient;
    private final VBox root = new VBox(15);
    private final TableView<Room> table = new TableView<>();
    private final Button refreshButton = new Button("Odśwież");
    private final Label statusLabel = new Label();
    private final ObservableList<Room> masterData = FXCollections.observableArrayList();

    public AdminRoomListPanel(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        // Set up listener for real-time room updates
        networkClient.setRoomEventListener(event -> handleRoomEvent(event));
        configureView();
    }

    private void configureView() {
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));

        Label titleLabel = new Label("Baza Pokoi Hotelowych");
        titleLabel.getStyleClass().add("header-label");

        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER);

        TextField capacityFilter = new TextField();
        capacityFilter.setPromptText("Min. miejsc");
        capacityFilter.getStyleClass().add("text-field");
        capacityFilter.setPrefWidth(90);

        TextField maxCapacityFilter = new TextField();
        maxCapacityFilter.setPromptText("Max. miejsc");
        maxCapacityFilter.getStyleClass().add("text-field");
        maxCapacityFilter.setPrefWidth(90);

        TextField minPriceFilter = new TextField();
        minPriceFilter.setPromptText("Cena od");
        minPriceFilter.getStyleClass().add("text-field");
        minPriceFilter.setPrefWidth(80);

        TextField maxPriceFilter = new TextField();
        maxPriceFilter.setPromptText("Cena do");
        maxPriceFilter.getStyleClass().add("text-field");
        maxPriceFilter.setPrefWidth(80);

        MenuButton amenitiesMenu = new MenuButton("Wymagane udogodnienia");
        amenitiesMenu.setStyle(
                "-fx-font-size: 13px; -fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 5;");

        CheckBox filterFridge = new CheckBox("Lodówka");
        CheckBox filterKitchenette = new CheckBox("Aneks kuchenny");
        CheckBox filterBalcony = new CheckBox("Balkon");
        CheckBox filterTv = new CheckBox("Telewizor");
        CheckBox filterTable = new CheckBox("Stół z krzesłami");

        CustomMenuItem item1 = new CustomMenuItem(filterFridge);
        item1.setHideOnClick(false);
        CustomMenuItem item2 = new CustomMenuItem(filterKitchenette);
        item2.setHideOnClick(false);
        CustomMenuItem item3 = new CustomMenuItem(filterBalcony);
        item3.setHideOnClick(false);
        CustomMenuItem item4 = new CustomMenuItem(filterTv);
        item4.setHideOnClick(false);
        CustomMenuItem item5 = new CustomMenuItem(filterTable);
        item5.setHideOnClick(false);
        amenitiesMenu.getItems().addAll(item1, item2, item3, item4, item5);

        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setStyle("-fx-padding: 8 15; -fx-min-height: 35px; -fx-font-size: 13px;");

        filterBox.getChildren().addAll(
                new Label("Miejsca:"), capacityFilter, new Label("-"), maxCapacityFilter,
                new Label(" PLN:"), minPriceFilter, new Label("-"), maxPriceFilter,
                amenitiesMenu, refreshButton);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(450);

        TableColumn<Room, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMaxWidth(60);

        TableColumn<Room, String> numCol = new TableColumn<>("Numer pokoju");
        numCol.setCellValueFactory(new PropertyValueFactory<>("number"));

        TableColumn<Room, Integer> capCol = new TableColumn<>("Ilość miejsc");
        capCol.setCellValueFactory(new PropertyValueFactory<>("capacity"));

        TableColumn<Room, Integer> bedsCol = new TableColumn<>("Ilość łóżek");
        bedsCol.setCellValueFactory(new PropertyValueFactory<>("bedCount"));

        TableColumn<Room, BigDecimal> priceCol = new TableColumn<>("Cena za noc (PLN)");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Room, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().isReserved() ? "Zajęty" : "Wolny"));

        TableColumn<Room, Void> actionCol = new TableColumn<>("Akcje");
        actionCol.setPrefWidth(180);
        actionCol.setMaxWidth(180);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button("Edytuj");
            private final Button deleteButton = new Button("Usuń");

            {
                editButton.getStyleClass().add("primary-button");
                deleteButton.getStyleClass().add("secondary-button");
                editButton.setOnAction(e -> openEditRoomDialog(getTableRow().getItem()));
                deleteButton.setOnAction(e -> deleteRoom(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox actions = new HBox(8, editButton, deleteButton);
                    actions.setAlignment(Pos.CENTER);
                    setGraphic(actions);
                }
            }
        });

        TableColumn<Room, String> amenitiesCol = new TableColumn<>("Udogodnienia pokoju");
        amenitiesCol.setCellValueFactory(new PropertyValueFactory<>("amenitiesText"));
        amenitiesCol.setPrefWidth(250);

        table.getColumns().addAll(idCol, numCol, capCol, bedsCol, priceCol, statusCol, amenitiesCol, actionCol);

        FilteredList<Room> filteredData = new FilteredList<>(masterData, p -> true);

        Runnable updateFilter = () -> {
            filteredData.setPredicate(room -> {
                if (!capacityFilter.getText().isEmpty()) {
                    try {
                        if (room.getCapacity() < Integer.parseInt(capacityFilter.getText().trim()))
                            return false;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!maxCapacityFilter.getText().isEmpty()) {
                    try {
                        if (room.getCapacity() > Integer.parseInt(maxCapacityFilter.getText().trim()))
                            return false;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!minPriceFilter.getText().isEmpty()) {
                    try {
                        if (room.getPrice().compareTo(new BigDecimal(minPriceFilter.getText().trim())) < 0)
                            return false;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!maxPriceFilter.getText().isEmpty()) {
                    try {
                        if (room.getPrice().compareTo(new BigDecimal(maxPriceFilter.getText().trim())) > 0)
                            return false;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (filterFridge.isSelected() && !room.isHasFridge())
                    return false;
                if (filterKitchenette.isSelected() && !room.isHasKitchenette())
                    return false;
                if (filterBalcony.isSelected() && !room.isHasBalcony())
                    return false;
                if (filterTv.isSelected() && !room.isHasTv())
                    return false;
                if (filterTable.isSelected() && !room.isHasTable())
                    return false;
                return true;
            });
        };

        capacityFilter.textProperty().addListener((obs, o, n) -> updateFilter.run());
        maxCapacityFilter.textProperty().addListener((obs, o, n) -> updateFilter.run());
        minPriceFilter.textProperty().addListener((obs, o, n) -> updateFilter.run());
        maxPriceFilter.textProperty().addListener((obs, o, n) -> updateFilter.run());
        filterFridge.selectedProperty().addListener((obs, o, n) -> updateFilter.run());
        filterKitchenette.selectedProperty().addListener((obs, o, n) -> updateFilter.run());
        filterBalcony.selectedProperty().addListener((obs, o, n) -> updateFilter.run());
        filterTv.selectedProperty().addListener((obs, o, n) -> updateFilter.run());
        filterTable.selectedProperty().addListener((obs, o, n) -> updateFilter.run());

        SortedList<Room> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        statusLabel.getStyleClass().add("status-label");

        refreshButton.setOnAction(e -> refreshRooms());

        Platform.runLater(this::refreshRooms);

        root.getChildren().addAll(titleLabel, filterBox, table, statusLabel);
    }

    private void refreshRooms() {
        refreshButton.setDisable(true);
        statusLabel.getStyleClass().removeAll("success", "error");
        statusLabel.setText("Pobieranie danych z bazy...");

        new Thread(() -> {
            try {
                List<Room> rooms = networkClient.requestAllRooms();
                Platform.runLater(() -> {
                    masterData.setAll(rooms);
                    statusLabel.setText("Pobrano " + rooms.size() + " pokoi z bazy.");
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
        }, "AdminRoomRefreshThread").start();
    }

    private void deleteRoom(Room room) {
        if (room == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Potwierdzenie usunięcia");
        alert.setHeaderText("Czy na pewno chcesz usunąć pokój " + room.getNumber() + "?");
        alert.setContentText("Tej operacji nie można cofnąć.");

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                statusLabel.getStyleClass().removeAll("success", "error");
                statusLabel.setText("Usuwanie pokoju...");

                new Thread(() -> {
                    try {
                        String response = networkClient.deleteRoom(room.getId());
                        Platform.runLater(() -> {
                            statusLabel.getStyleClass().removeAll("success", "error");
                            if (response.startsWith("OK")) {
                                statusLabel.setText("Pokój usunięty pomyślnie.");
                                statusLabel.getStyleClass().add("success");
                                refreshRooms();
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
                }, "AdminRoomDeleteThread").start();
            }
        });
    }

    private void openEditRoomDialog(Room room) {
        if (room == null) {
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Edytuj pokój " + room.getNumber());

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        TextField roomNumberField = new TextField(room.getNumber());
        TextField capacityField = new TextField(String.valueOf(room.getCapacity()));
        TextField bedCountField = new TextField(String.valueOf(room.getBedCount()));
        TextField priceField = new TextField(room.getPrice().toPlainString());

        CheckBox chkFridge = new CheckBox("Lodówka");
        chkFridge.setSelected(room.isHasFridge());
        CheckBox chkKitchenette = new CheckBox("Aneks kuchenny");
        chkKitchenette.setSelected(room.isHasKitchenette());
        CheckBox chkBalcony = new CheckBox("Balkon");
        chkBalcony.setSelected(room.isHasBalcony());
        CheckBox chkTv = new CheckBox("Telewizor");
        chkTv.setSelected(room.isHasTv());
        CheckBox chkTable = new CheckBox("Stół z krzesłami");
        chkTable.setSelected(room.isHasTable());

        grid.add(new Label("Numer pokoju:"), 0, 0);
        grid.add(roomNumberField, 1, 0);
        grid.add(new Label("Ilość miejsc:"), 0, 1);
        grid.add(capacityField, 1, 1);
        grid.add(new Label("Ilość łóżek:"), 0, 2);
        grid.add(bedCountField, 1, 2);
        grid.add(new Label("Cena za noc (PLN):"), 0, 3);
        grid.add(priceField, 1, 3);

        VBox amenitiesBox = new VBox(10);
        amenitiesBox.getChildren().addAll(chkFridge, chkKitchenette, chkBalcony, chkTv, chkTable);
        grid.add(new Label("Udogodnienia:"), 0, 4);
        grid.add(amenitiesBox, 1, 4);

        List<RoomImage> currentRoomImages = new ArrayList<>();
        Set<Integer> imagesToDelete = new HashSet<>();
        List<File> newImages = new ArrayList<>();

        FlowPane existingImagesPane = new FlowPane(10, 10);
        existingImagesPane.setPrefWrapLength(500);
        existingImagesPane.setPadding(new Insets(8));
        existingImagesPane.getChildren().add(new Label("Ładowanie zdjęć..."));

        Button chooseNewImagesButton = new Button("Dodaj zdjęcia");
        chooseNewImagesButton.getStyleClass().add("secondary-button");
        Button clearNewImagesButton = new Button("Wyczyść nowe zdjęcia");
        clearNewImagesButton.getStyleClass().add("secondary-button");
        Label selectedNewImagesLabel = new Label("Brak nowych zdjęć");
        selectedNewImagesLabel.setWrapText(true);

        chooseNewImagesButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Wybierz nowe zdjęcia pokoju");
            fileChooser.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter("Obrazy", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null && !files.isEmpty()) {
                int addedCount = 0;
                for (File file : files) {
                    if (file != null && !newImages.contains(file)) {
                        newImages.add(file);
                        addedCount++;
                    }
                }
                if (addedCount > 0) {
                    selectedNewImagesLabel.setText(formatSelectedFileNames(newImages));
                }
            }
        });

        clearNewImagesButton.setOnAction(e -> {
            newImages.clear();
            selectedNewImagesLabel.setText("Brak nowych zdjęć");
        });

        grid.add(new Label("Obecne zdjęcia:"), 0, 5);
        grid.add(existingImagesPane, 1, 5);
        grid.add(new Label("Nowe zdjęcia:"), 0, 6);
        HBox imageButtons = new HBox(10, chooseNewImagesButton, clearNewImagesButton);
        imageButtons.setAlignment(Pos.CENTER_LEFT);
        grid.add(imageButtons, 1, 6);
        grid.add(selectedNewImagesLabel, 1, 7);

        Label dialogStatus = new Label();
        dialogStatus.getStyleClass().add("status-label");

        Button saveButton = new Button("Zapisz");
        saveButton.getStyleClass().add("primary-button");
        Button cancelButton = new Button("Anuluj");
        cancelButton.getStyleClass().add("secondary-button");

        saveButton.setOnAction(e -> {
            String number = roomNumberField.getText().trim();
            String capacityText = capacityField.getText().trim();
            String bedCountText = bedCountField.getText().trim();
            String priceText = priceField.getText().trim();

            dialogStatus.getStyleClass().removeAll("success", "error");
            if (number.isEmpty() || capacityText.isEmpty() || bedCountText.isEmpty() || priceText.isEmpty()) {
                dialogStatus.setText("Wypełnij wszystkie pola przed zapisaniem.");
                dialogStatus.getStyleClass().add("error");
                return;
            }

            saveButton.setDisable(true);
            dialogStatus.setText("Wysyłanie zmian...");

            new Thread(() -> {
                try {
                    String response = networkClient.updateRoom(
                            room.getId(), number, capacityText, bedCountText, priceText,
                            room.isReserved(), chkFridge.isSelected(), chkKitchenette.isSelected(),
                            chkBalcony.isSelected(), chkTv.isSelected(), chkTable.isSelected());

                    Platform.runLater(() -> {
                        dialogStatus.getStyleClass().removeAll("success", "error");
                        if (!response.startsWith("OK")) {
                            String errorMsg = response.contains(";") ? response.split(";", 2)[1] : response;
                            dialogStatus.setText("Błąd: " + errorMsg);
                            dialogStatus.getStyleClass().add("error");
                            saveButton.setDisable(false);
                            return;
                        }

                        new Thread(() -> {
                            String imageError = null;
                            try {
                                if (!imagesToDelete.isEmpty()) {
                                    imageError = deleteRoomImages(imagesToDelete);
                                }
                                if (imageError == null && !newImages.isEmpty()) {
                                    imageError = uploadRoomImages(room.getId(), newImages);
                                }
                            } catch (IOException ex) {
                                imageError = ex.getMessage();
                            }

                            final String finalImageError = imageError;
                            Platform.runLater(() -> {
                                if (finalImageError == null) {
                                    dialogStatus.setText("Pokój zaktualizowany pomyślnie.");
                                    dialogStatus.getStyleClass().add("success");
                                    refreshRooms();
                                    dialog.close();
                                } else {
                                    dialogStatus.setText("Pokój zaktualizowany, ale: " + finalImageError);
                                    dialogStatus.getStyleClass().add("error");
                                    saveButton.setDisable(false);
                                }
                            });
                        }, "AdminRoomImageUpdateThread").start();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        dialogStatus.getStyleClass().removeAll("success", "error");
                        dialogStatus.setText("Błąd połączenia: " + ex.getMessage());
                        dialogStatus.getStyleClass().add("error");
                        saveButton.setDisable(false);
                    });
                }
            }, "AdminRoomUpdateThread").start();
        });

        cancelButton.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, saveButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane formScrollPane = new ScrollPane(grid);
        formScrollPane.setFitToWidth(true);
        formScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox container = new VBox(15, formScrollPane, buttons, dialogStatus);
        container.setPadding(new Insets(15));

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double dialogWidth = Math.min(820, screenBounds.getWidth() * 0.9);
        double dialogHeight = Math.min(760, screenBounds.getHeight() * 0.9);
        formScrollPane.setPrefViewportWidth(dialogWidth - 60);
        formScrollPane.setPrefViewportHeight(dialogHeight - 150);

        dialog.setMinWidth(Math.min(720, dialogWidth));
        dialog.setMinHeight(Math.min(620, dialogHeight));
        dialog.setScene(new Scene(container, dialogWidth, dialogHeight));
        dialog.show();

        new Thread(() -> {
            try {
                List<RoomImage> images = networkClient.requestRoomImages(room.getId());
                Platform.runLater(() -> {
                    currentRoomImages.addAll(images);
                    refreshExistingImageGallery(existingImagesPane, currentRoomImages, imagesToDelete);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    dialogStatus.getStyleClass().removeAll("success", "error");
                    dialogStatus.setText("Błąd ładowania zdjęć: " + ex.getMessage());
                    dialogStatus.getStyleClass().add("error");
                    refreshExistingImageGallery(existingImagesPane, currentRoomImages, imagesToDelete);
                });
            }
        }, "LoadRoomImagesThread").start();
    }

    private void refreshExistingImageGallery(FlowPane imagesPane, List<RoomImage> currentRoomImages,
            Set<Integer> imagesToDelete) {
        imagesPane.getChildren().clear();
        if (currentRoomImages.isEmpty()) {
            imagesPane.getChildren().add(new Label("Brak zdjęć"));
            return;
        }

        for (RoomImage image : new ArrayList<>(currentRoomImages)) {
            ImageView imageView = new ImageView(new Image(new ByteArrayInputStream(image.getData())));
            imageView.setFitWidth(140);
            imageView.setFitHeight(90);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            Label fileNameLabel = new Label(image.getFileName());
            fileNameLabel.setWrapText(true);
            fileNameLabel.setMaxWidth(140);

            Button removeButton = new Button("Usuń");
            removeButton.getStyleClass().add("secondary-button");
            removeButton.setOnAction(e -> {
                currentRoomImages.remove(image);
                imagesToDelete.add(image.getId());
                refreshExistingImageGallery(imagesPane, currentRoomImages, imagesToDelete);
            });

            VBox imageCard = new VBox(8, imageView, fileNameLabel, removeButton);
            imageCard.setAlignment(Pos.CENTER);
            imageCard.setStyle("-fx-border-color: #d9d9d9; -fx-border-radius: 6; -fx-padding: 8;");
            imagesPane.getChildren().add(imageCard);
        }
    }

    private String formatSelectedFileNames(List<File> files) {
        if (files.isEmpty()) {
            return "Brak nowych zdjęć";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(files.get(i).getName());
            if (i >= 4 && files.size() > 5) {
                builder.append(", +" + (files.size() - 5) + " więcej");
                break;
            }
        }
        return builder.toString();
    }

    private String deleteRoomImages(Set<Integer> imageIds) throws IOException {
        for (Integer imageId : imageIds) {
            String response = networkClient.deleteRoomImage(imageId);
            if (!response.startsWith("OK")) {
                return response.contains(";") ? response.split(";", 2)[1] : response;
            }
        }
        return null;
    }

    private String uploadRoomImages(int roomId, List<File> selectedImages) throws IOException {
        for (File file : selectedImages) {
            if (file == null || !file.exists()) {
                return "Nieprawidłowy plik obrazu: " + (file == null ? "brak" : file.getName());
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = fis.readAllBytes();
                String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
                String safeFileName = file.getName().replace(";", "_").replace("\n", "_").replace("\r", "_");
                String response = networkClient.addRoomImage(roomId, safeFileName, encoded);
                if (!response.startsWith("OK")) {
                    return response.contains(";") ? response.split(";", 2)[1] : response;
                }
            }
        }
        return null;
    }

    public VBox getRoot() {
        return root;
    }

    private void handleRoomEvent(RoomEvent event) {
        // Handle real-time room updates from server
        switch (event.getType()) {
            case ROOM_ADDED:
                if (event.getRoom() != null) {
                    masterData.add(event.getRoom());
                    statusLabel.setText("Nowy pokój dodany!");
                    statusLabel.getStyleClass().removeAll("success", "error");
                    statusLabel.getStyleClass().add("success");
                }
                break;
            case ROOM_UPDATED:
                if (event.getRoom() != null) {
                    for (int i = 0; i < masterData.size(); i++) {
                        if (masterData.get(i).getId() == event.getRoom().getId()) {
                            masterData.set(i, event.getRoom());
                            statusLabel.setText("Pokój zaktualizowany!");
                            statusLabel.getStyleClass().removeAll("success", "error");
                            statusLabel.getStyleClass().add("success");
                            break;
                        }
                    }
                }
                break;
            case ROOM_DELETED:
                if (event.getRoom() != null) {
                    masterData.removeIf(room -> room.getId() == event.getRoom().getId());
                    statusLabel.setText("Pokój usunięty.");
                    statusLabel.getStyleClass().removeAll("success", "error");
                    statusLabel.getStyleClass().add("success");
                }
                break;
            case ROOM_RESERVED:
                if (event.getRoom() != null) {
                    for (int i = 0; i < masterData.size(); i++) {
                        if (masterData.get(i).getId() == event.getRoom().getId()) {
                            masterData.set(i, event.getRoom());
                            break;
                        }
                    }
                }
                break;
            case ROOM_IMAGE_ADDED:
                // Room image was added - could refresh images display if needed
                break;
        }
    }
}
