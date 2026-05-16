package client.ui;

import client.network.NetworkClient;
import core.model.Room;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.util.List;

public class ClientController {
    private final NetworkClient networkClient;
    private final ObservableList<String> roomListItems = FXCollections.observableArrayList();

    private final BorderPane rootPane = new BorderPane();
    private final ListView<String> roomsListView = new ListView<>(roomListItems);
    private final Button loadRoomsButton = new Button("Load Available Rooms");
    private final Label statusLabel = new Label("Ready");

    public ClientController(NetworkClient networkClient) {
        this.networkClient = networkClient;
        configureView();
    }

    private void configureView() {
        loadRoomsButton.setOnAction(event -> loadRooms());
        rootPane.setCenter(roomsListView);
        rootPane.setTop(loadRoomsButton);
        rootPane.setBottom(statusLabel);
    }

    public BorderPane getRootPane() {
        return rootPane;
    }

    private void loadRooms() {
        statusLabel.setText("Loading rooms...");
        roomListItems.clear();

        Thread backgroundThread = new Thread(() -> {
            try {
                List<Room> rooms = networkClient.requestAvailableRooms();
                Platform.runLater(() -> updateRoomList(rooms));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Network error: " + e.getMessage()));
            }
        }, "RoomLoaderThread");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void updateRoomList(List<Room> rooms) {
        if (rooms.isEmpty()) {
            statusLabel.setText("No available rooms found.");
            return;
        }
        rooms.stream()
                .map(Room::toString)
                .forEach(roomListItems::add);
        statusLabel.setText("Loaded " + rooms.size() + " room(s).");
    }
}
