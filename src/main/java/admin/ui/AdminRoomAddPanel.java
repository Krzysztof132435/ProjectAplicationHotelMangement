package admin.ui;

import admin.network.AdminNetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AdminRoomAddPanel {
    private final AdminNetworkClient networkClient;
    private final VBox root = new VBox(20);

    public AdminRoomAddPanel(AdminNetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        Label titleLabel = new Label("Dodawanie Nowego Pokoju do Bazy");
        titleLabel.getStyleClass().add("header-label");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(15);
        grid.setVgap(15);
        grid.getStyleClass().add("form-box");

        TextField roomNumberField = new TextField();
        roomNumberField.setPromptText("np. 101");
        roomNumberField.getStyleClass().add("text-field");

        TextField capacityField = new TextField();
        capacityField.setPromptText("np. 2");
        capacityField.getStyleClass().add("text-field");

        TextField bedCountField = new TextField();
        bedCountField.setPromptText("np. 2");
        bedCountField.getStyleClass().add("text-field");

        TextField priceField = new TextField();
        priceField.setPromptText("np. 150.00");
        priceField.getStyleClass().add("text-field");

        grid.add(new Label("Numer pokoju:"), 0, 0);
        grid.add(roomNumberField, 1, 0);
        grid.add(new Label("Ilość miejsc:"), 0, 1);
        grid.add(capacityField, 1, 1);
        grid.add(new Label("Ilość łóżek:"), 0, 2);
        grid.add(bedCountField, 1, 2);
        grid.add(new Label("Cena za noc (PLN):"), 0, 3);
        grid.add(priceField, 1, 3);

        VBox amenitiesBox = new VBox(10);
        Label amenitiesLabel = new Label("Wybierz udogodnienia:");
        amenitiesLabel.setStyle("-fx-font-weight: bold;");

        CheckBox chkFridge = new CheckBox("Lodówka");
        CheckBox chkKitchenette = new CheckBox("Aneks kuchenny");
        CheckBox chkBalcony = new CheckBox("Balkon");
        CheckBox chkTv = new CheckBox("Telewizor");
        CheckBox chkTable = new CheckBox("Stół z krzesłami");

        amenitiesBox.getChildren().addAll(amenitiesLabel, chkFridge, chkKitchenette, chkBalcony, chkTv, chkTable);
        grid.add(amenitiesBox, 1, 4);

        List<File> selectedImages = new ArrayList<>();
        Button chooseImagesButton = new Button("Dodaj zdjęcia");
        chooseImagesButton.getStyleClass().add("secondary-button");
        Button clearImagesButton = new Button("Wyczyść zdjęcia");
        clearImagesButton.getStyleClass().add("secondary-button");
        Label selectedImagesLabel = new Label("Brak wybranych zdjęć");
        selectedImagesLabel.setWrapText(true);

        chooseImagesButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Wybierz zdjęcia pokoju");
            fileChooser.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter("Obrazy", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));
            List<File> files = fileChooser
                    .showOpenMultipleDialog(root.getScene() != null ? root.getScene().getWindow() : null);
            if (files != null && !files.isEmpty()) {
                int addedCount = 0;
                for (File file : files) {
                    if (file != null && !selectedImages.contains(file)) {
                        selectedImages.add(file);
                        addedCount++;
                    }
                }
                if (addedCount > 0) {
                    selectedImagesLabel.setText(formatSelectedImagesLabel(selectedImages));
                }
            }
        });

        clearImagesButton.setOnAction(e -> {
            selectedImages.clear();
            selectedImagesLabel.setText("Brak wybranych zdjęć");
        });

        grid.add(chooseImagesButton, 0, 5);
        grid.add(clearImagesButton, 1, 5);
        grid.add(selectedImagesLabel, 0, 6, 2, 1);

        Button addButton = new Button("Dodaj pokój");
        addButton.getStyleClass().add("primary-button");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        addButton.setOnAction(e -> {
            String num = roomNumberField.getText().trim();
            String cap = capacityField.getText().trim();
            String beds = bedCountField.getText().trim();
            String price = priceField.getText().trim();

            statusLabel.getStyleClass().removeAll("success", "error");

            if (num.isEmpty() || cap.isEmpty() || beds.isEmpty() || price.isEmpty()) {
                statusLabel.setText("Wypełnij podstawowe pola (Numer, Miejsca, Łóżka, Cena)!");
                statusLabel.getStyleClass().add("error");
                return;
            }

            addButton.setDisable(true);
            statusLabel.setText("Wysyłanie do serwera...");

            new Thread(() -> {
                try {
                    String response = networkClient.addRoom(num, cap, beds, price, false,
                            chkFridge.isSelected(), chkKitchenette.isSelected(), chkBalcony.isSelected(),
                            chkTv.isSelected(), chkTable.isSelected());

                    String statusMessage;
                    String statusStyle;
                    if (response.startsWith("OK")) {
                        int roomId = -1;
                        String[] responseParts = response.split(";");
                        if (responseParts.length > 1) {
                            try {
                                roomId = Integer.parseInt(responseParts[1]);
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        String imageUploadError = null;
                        if (roomId > 0 && !selectedImages.isEmpty()) {
                            imageUploadError = uploadRoomImages(roomId, selectedImages);
                        }

                        if (imageUploadError == null) {
                            statusMessage = "Sukces: Pokój został pomyślnie dodany!";
                            statusStyle = "success";
                        } else {
                            statusMessage = "Pokój dodany, ale problem z przesyłaniem zdjęć: " + imageUploadError;
                            statusStyle = "error";
                        }
                    } else {
                        String errorMsg = response.contains(";") ? response.split(";", 2)[1] : response;
                        statusMessage = "Błąd: " + errorMsg;
                        statusStyle = "error";
                    }

                    final String finalStatusMessage = statusMessage;
                    final String finalStatusStyle = statusStyle;

                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        statusLabel.setText(finalStatusMessage);
                        statusLabel.getStyleClass().add(finalStatusStyle);
                        if (finalStatusStyle.equals("success")) {
                            roomNumberField.clear();
                            capacityField.clear();
                            bedCountField.clear();
                            priceField.clear();
                            chkFridge.setSelected(false);
                            chkKitchenette.setSelected(false);
                            chkBalcony.setSelected(false);
                            chkTv.setSelected(false);
                            chkTable.setSelected(false);
                            selectedImages.clear();
                            selectedImagesLabel.setText("Brak wybranych zdjęć");
                        }
                        addButton.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.getStyleClass().removeAll("success", "error");
                        statusLabel.setText("Błąd połączenia: " + ex.getMessage());
                        statusLabel.getStyleClass().add("error");
                        addButton.setDisable(false);
                    });
                }
            }).start();
        });

        root.getChildren().addAll(titleLabel, grid, addButton, statusLabel);
    }

    private String uploadRoomImages(int roomId, List<File> selectedImages) {
        for (File file : selectedImages) {
            if (file == null || !file.exists()) {
                return "Nieprawidłowy plik obrazu: " + (file == null ? "brak" : file.getName());
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = fis.readAllBytes();
                String encoded = Base64.getEncoder().encodeToString(bytes);
                String safeFileName = file.getName().replace(";", "_").replace("\n", "_").replace("\r", "_");
                String response = networkClient.addRoomImage(roomId, safeFileName, encoded);
                if (!response.startsWith("OK")) {
                    return response.contains(";") ? response.split(";", 2)[1] : response;
                }
            } catch (IOException e) {
                return "Nie udało się wczytać obrazu " + file.getName() + ": " + e.getMessage();
            }
        }
        return null;
    }

    private String formatSelectedImagesLabel(List<File> selectedImages) {
        if (selectedImages.isEmpty()) {
            return "Brak wybranych zdjęć";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(selectedImages.size()).append(" plików wybranych: ");
        for (int i = 0; i < selectedImages.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            String name = selectedImages.get(i).getName();
            if (name.length() > 25) {
                name = name.substring(0, 22) + "...";
            }
            builder.append(name);
            if (i >= 4 && selectedImages.size() > 5) {
                builder.append(", +" + (selectedImages.size() - 5) + " więcej");
                break;
            }
        }
        return builder.toString();
    }

    public VBox getRoot() {
        return root;
    }
}