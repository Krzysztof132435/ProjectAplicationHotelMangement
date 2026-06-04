package admin.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.math.BigDecimal;
import core.event.RoomEvent;
import core.model.Reservation;
import core.model.Room;
import core.model.RoomImage;
import java.util.ArrayList;
import java.util.List;
import core.model.Client;
import javafx.application.Platform;

public class AdminNetworkClient {
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
    private RoomEventListener roomEventListener;
    private ClientEventListener clientEventListener;
    private ReservationEventListener reservationEventListener;

    public AdminNetworkClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
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
                        String[] parts = line.split(";", 3);
                        if (parts.length >= 3 && clientEventListener != null) {
                            String action = parts[1];
                            String username = parts[2];
                            Platform.runLater(() -> clientEventListener.onClientEvent(action, username));
                        }
                    } else if (line.startsWith("RESERVATION_EVENT;")) {
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
        }, "AdminNetworkEventListener");
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

    public String login(String username, String password) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("ADMIN_LOGIN;" + username + ";" + password + "\n");
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

    public String addRoom(String number, String capacity, String bedCount, String price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String payload = String.format("ADD_ROOM;%s;%s;%s;%s;%b;%b;%b;%b;%b;%b\n",
                    number, capacity, bedCount, price, isReserved, hasFridge, hasKitchenette, hasBalcony, hasTv,
                    hasTable);
            writer.write(payload);
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

    public String updateRoom(int id, String number, String capacity, String bedCount, String price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String payload = String.format("UPDATE_ROOM;%d;%s;%s;%s;%s;%b;%b;%b;%b;%b;%b\n",
                    id, number, capacity, bedCount, price, isReserved, hasFridge, hasKitchenette,
                    hasBalcony, hasTv, hasTable);
            writer.write(payload);
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

    public String deleteRoom(int roomId) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("DELETE_ROOM;" + roomId + "\n");
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

    public String addRoomImage(int roomId, String fileName, String imageBase64) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String safeFileName = fileName.replace(";", "_").replace("\n", "_").replace("\r", "_");
            String payload = String.format("ADD_ROOM_IMAGE;%d;%s;%s\n", roomId, safeFileName, imageBase64);
            writer.write(payload);
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
                if (line.startsWith("ROOM_EVENT;"))
                    continue;
                if (line.startsWith("ROOM_IMAGE;")) {
                    images.add(parseRoomImage(line));
                }
            }
            return images;
        }
    }

    public String deleteRoomImage(int imageId) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("DELETE_ROOM_IMAGE;" + imageId + "\n");
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

    private RoomImage parseRoomImage(String line) {
        String[] parts = line.split(";", 4);
        int id = Integer.parseInt(parts[1]);
        String fileName = parts[2];
        byte[] data = java.util.Base64.getDecoder().decode(parts[3]);
        return new RoomImage(id, fileName, data);
    }

    public String generateReport() throws IOException {
        return generateReport("raport_rezerwacji", null, null);
    }

    public String generateReport(String reportName, LocalDate fromDate, LocalDate toDate) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String safeReportName = reportName == null ? "" : reportName.replace(";", "_").trim();
            String from = fromDate == null ? "" : fromDate.toString();
            String to = toDate == null ? "" : toDate.toString();
            writer.write("GENERATE_REPORT;" + safeReportName + ";" + from + ";" + to + "\n");
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

    public List<Room> requestAllRooms() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("LIST_ROOMS\n");
            writer.flush();

            List<Room> rooms = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END"))
                    break;
                if (line.startsWith("ROOM;")) {
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

                    rooms.add(new Room(id, number, capacity, bedCount, price,
                            isReserved, hasFridge, hasKitchenette, hasBalcony, hasTv, hasTable));
                }
            }
            return rooms;
        }
    }

    public List<Reservation> requestAllReservations() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("LIST_RESERVATIONS\n");
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

    public String updateReservationStatus(int reservationId, String status) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("UPDATE_RESERVATION;" + reservationId + ";" + status + "\n");
            writer.flush();

            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END"))
                    break;
                return response;
            }
            return "ERROR;Brak odpowiedzi z serwera";
        }
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

    public List<Client> requestAllClients() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("LIST_CLIENTS\n");
            writer.flush();

            List<Client> clients = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END"))
                    break;
                if (line.startsWith("CLIENT;")) {
                    String[] parts = line.split(";", -1);
                    clients.add(new Client(Integer.parseInt(parts[1]), parts[2], "", false, parts[3]));
                }
            }
            return clients;
        }
    }

    public String deleteClient(int clientId) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("DELETE_CLIENT;" + clientId + "\n");
            writer.flush();

            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END"))
                    break;
                return response;
            }
            return "ERROR;Brak odpowiedzi z serwera";
        }
    }
}