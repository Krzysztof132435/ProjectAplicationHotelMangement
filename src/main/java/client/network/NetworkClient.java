package client.network;

import core.event.RoomEvent;
import core.model.Reservation;
import core.model.Room;
import core.model.RoomImage;
import core.model.RoomSearchFilter;
import javafx.application.Platform;
import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class NetworkClient {
    public interface RoomEventListener {
        void onRoomEvent(RoomEvent event);
    }

    public interface ClientEventListener {
        void onClientEvent(String action, String username);
    }

    public interface ReservationEventListener {
        void onReservationEvent(String action, String username, int roomId);
    }

    private final String serverHost;
    private final int serverPort;
    private Socket socket;
    private Thread eventListenerThread;
    private Runnable onRefresh;
    private RoomEventListener roomEventListener;
    private ClientEventListener clientEventListener;
    private ReservationEventListener reservationEventListener;

    public NetworkClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public void setOnRefresh(Runnable onRefresh) {
        this.onRefresh = onRefresh;
    }

    public void setRoomEventListener(RoomEventListener listener) {
        this.roomEventListener = listener;
    }

    public void setClientEventListener(ClientEventListener listener) {
        this.clientEventListener = listener;
    }

    public void setReservationEventListener(ReservationEventListener listener) {
        this.reservationEventListener = listener;
    }

    public void startListening() {
        stopListening();
        eventListenerThread = new Thread(() -> {
            try {
                this.socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer.write("SUBSCRIBE_EVENTS\n");
                writer.flush();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("ROOM_EVENT;")) {
                        RoomEvent event = parseRoomEvent(line);
                        if (event != null && roomEventListener != null) {
                            Platform.runLater(() -> roomEventListener.onRoomEvent(event));
                        }
                    } else if (line.startsWith("CLIENT_EVENT;")) {
                        // Format: CLIENT_EVENT;ADDED;username
                        String[] parts = line.split(";", 3);
                        if (parts.length >= 3 && clientEventListener != null) {
                            String action = parts[1];
                            String username = parts[2];
                            Platform.runLater(() -> clientEventListener.onClientEvent(action, username));
                        }
                    } else if (line.startsWith("RESERVATION_EVENT;")) {
                        // Format: RESERVATION_EVENT;ADDED;username;roomId
                        String[] parts = line.split(";", 4);
                        if (parts.length >= 4 && reservationEventListener != null) {
                            String action = parts[1];
                            String username = parts[2];
                            int roomId = Integer.parseInt(parts[3]);
                            Platform.runLater(
                                    () -> reservationEventListener.onReservationEvent(action, username, roomId));
                        }
                    }
                }
            } catch (IOException e) {
                if (!(e instanceof java.net.SocketException)) {
                    e.printStackTrace();
                }
            }
        }, "NetworkEventListener");
        eventListenerThread.setDaemon(true);
        eventListenerThread.start();
    }

    public void stopListening() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
        if (eventListenerThread != null && eventListenerThread.isAlive()) {
            eventListenerThread.interrupt();
            eventListenerThread = null;
        }
    }

    public List<Room> requestAvailableRooms() throws IOException {
        return sendRoomCommand("LIST_ROOMS\n");
    }

    public List<Room> searchRooms(RoomSearchFilter filter) throws IOException {
        StringBuilder cmd = new StringBuilder("SEARCH_ROOMS");
        cmd.append(";").append(filter.getMinCapacity() > 0 ? filter.getMinCapacity() : -1);
        cmd.append(";").append(filter.getMaxPrice() != null ? filter.getMaxPrice().toPlainString() : -1);
        cmd.append(";").append(filter.isOnlyAvailable() ? 1 : 0);
        cmd.append(";").append(filter.getCheckIn() != null ? filter.getCheckIn() : "");
        cmd.append(";").append(filter.getCheckOut() != null ? filter.getCheckOut() : "");
        cmd.append(";").append(filter.isRequireFridge() ? 1 : 0);
        cmd.append(";").append(filter.isRequireKitchenette() ? 1 : 0);
        cmd.append(";").append(filter.isRequireBalcony() ? 1 : 0);
        cmd.append(";").append(filter.isRequireTv() ? 1 : 0);
        cmd.append(";").append(filter.isRequireTable() ? 1 : 0);
        cmd.append("\n");
        return sendRoomCommand(cmd.toString());
    }

    public String reserveRoom(int roomId, String username, LocalDate checkIn, LocalDate checkOut) throws IOException {
        String request = String.format("RESERVE;%d;%s;%s;%s\n",
                roomId, username, checkIn, checkOut);
        return sendSingleResponse(request);
    }

    public List<Reservation> getMyReservations(String username) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("LIST_MY_RESERVATIONS;" + username + "\n");
            writer.flush();

            List<Reservation> reservations = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END"))
                    break;
                if (line.startsWith("RESERVATION;")) {
                    reservations.add(parseReservation(line));
                }
            }
            return reservations;
        }
    }

    public String deleteReservation(int reservationId) throws IOException {
        return sendSingleResponse("DELETE_RESERVATION;" + reservationId + "\n");
    }

    public String login(String username, String password) throws IOException {
        return sendSingleResponse("LOGIN;" + username + ";" + password + "\n");
    }

    public String register(String username, String password, String email) throws IOException {
        return sendSingleResponse("REGISTER;" + username + ";" + password + ";" + email + "\n");
    }

    public String requestPasswordReset(String username) throws IOException {
        return sendSingleResponse("REQUEST_PASSWORD_RESET;" + username + "\n");
    }

    public String resetPassword(String resetToken, String newPassword) throws IOException {
        return sendSingleResponse("RESET_PASSWORD;" + resetToken + ";" + newPassword + "\n");
    }

    private List<Room> sendRoomCommand(String request) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(request);
            writer.flush();

            List<Room> rooms = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END"))
                    break;
                if (line.startsWith("ROOM;")) {
                    rooms.add(parseRoom(line));
                }
            }
            return rooms;
        }
    }

    private String sendSingleResponse(String request) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(request);
            writer.flush();

            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END"))
                    break;
                if (response.startsWith("ROOM_EVENT;"))
                    continue;
                return response;
            }
            return "ERROR;Brak odpowiedzi z serwera";
        }
    }

    private Room parseRoom(String line) {
        String[] parts = line.split(";");
        int id = Integer.parseInt(parts[1]);
        String number = parts[2];
        int capacity = Integer.parseInt(parts[3]);
        int bedCount = Integer.parseInt(parts[4]);
        BigDecimal price = new BigDecimal(parts[5]);
        boolean isReserved = Boolean.parseBoolean(parts[6]);
        boolean hasFridge = Boolean.parseBoolean(parts[7]);
        boolean hasKitchenette = Boolean.parseBoolean(parts[8]);
        boolean hasBalcony = Boolean.parseBoolean(parts[9]);
        boolean hasTv = Boolean.parseBoolean(parts[10]);
        boolean hasTable = Boolean.parseBoolean(parts[11]);
        return new Room(id, number, capacity, bedCount, price, isReserved, hasFridge, hasKitchenette, hasBalcony, hasTv,
                hasTable);
    }

    public List<RoomImage> requestRoomImages(int roomId) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("GET_ROOM_IMAGES;" + roomId + "\n");
            writer.flush();

            List<RoomImage> images = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END"))
                    break;
                if (line.startsWith("ROOM_IMAGE;")) {
                    images.add(parseRoomImage(line));
                }
            }
            return images;
        }
    }

    private RoomImage parseRoomImage(String line) {
        String[] parts = line.split(";", 4);
        int id = Integer.parseInt(parts[1]);
        String fileName = parts[2];
        byte[] data = Base64.getDecoder().decode(parts[3]);
        return new RoomImage(id, fileName, data);
    }

    private Reservation parseReservation(String line) {
        String[] parts = line.split(";", -1);
        return new Reservation(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts[3],
                parts[4],
                parts[5],
                parts[6],
                parts.length > 7 && !parts[7].isEmpty() ? parts[7] : null,
                parts.length > 8 && !parts[8].isEmpty() ? parts[8] : null);
    }

    private RoomEvent parseRoomEvent(String line) {
        try {
            String[] parts = line.split(";", 3);
            String eventTypeName = parts[1];
            RoomEvent.EventType eventType = RoomEvent.EventType.valueOf(eventTypeName);

            if (parts.length < 3 || "null".equals(parts[2])) {
                return new RoomEvent(eventType, null);
            }

            String[] roomParts = parts[2].split(";");
            if (roomParts.length < 11) {
                return new RoomEvent(eventType, null);
            }

            int id = Integer.parseInt(roomParts[0]);
            String number = roomParts[1];
            int capacity = Integer.parseInt(roomParts[2]);
            int bedCount = Integer.parseInt(roomParts[3]);
            BigDecimal price = new BigDecimal(roomParts[4]);
            boolean isReserved = Boolean.parseBoolean(roomParts[5]);
            boolean hasFridge = Boolean.parseBoolean(roomParts[6]);
            boolean hasKitchenette = Boolean.parseBoolean(roomParts[7]);
            boolean hasBalcony = Boolean.parseBoolean(roomParts[8]);
            boolean hasTv = Boolean.parseBoolean(roomParts[9]);
            boolean hasTable = Boolean.parseBoolean(roomParts[10]);

            Room room = new Room(id, number, capacity, bedCount, price, isReserved, hasFridge,
                    hasKitchenette, hasBalcony, hasTv, hasTable);

            return new RoomEvent(eventType, room);
        } catch (Exception e) {
            System.err.println("Error parsing room event: " + e.getMessage());
            return null;
        }
    }
}
