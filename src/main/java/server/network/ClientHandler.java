package server.network;

import core.event.EventBus;
import core.event.EventListener;
import core.event.RoomEvent;
import core.model.Room;
import core.model.RoomSearchFilter;
import server.report.ReportGenerator;
import server.service.EmailService;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import server.database.DatabaseManager;

public class ClientHandler implements Runnable, EventListener {
    private final Socket clientSocket;
    private final DatabaseManager databaseManager;
    private BufferedWriter writer;
    private boolean subscribedToEvents = false;

    public ClientHandler(Socket clientSocket, DatabaseManager databaseManager) {
        this.clientSocket = clientSocket;
        this.databaseManager = databaseManager;
    }

    @Override
    public void onRoomEvent(RoomEvent event) {
        if (subscribedToEvents) {
            sendMessage(formatRoomEventMessage(event));
        }
    }

    private String formatRoomEventMessage(RoomEvent event) {
        Room room = event.getRoom();
        if (room == null) {
            return "ROOM_EVENT;" + event.getType().name() + ";null\n";
        }
        return String.format("ROOM_EVENT;%s;%d;%s;%d;%d;%s;%b;%b;%b;%b;%b;%b\n",
                event.getType().name(),
                room.getId(), room.getNumber(), room.getCapacity(), room.getBedCount(),
                room.getPrice().toPlainString(),
                room.isReserved(), room.isHasFridge(), room.isHasKitchenette(),
                room.isHasBalcony(), room.isHasTv(), room.isHasTable());
    }

    @Override
    public void run() {
        HotelServer.clients.add(this);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {
            this.writer = writer;
            String request = reader.readLine();
            if (request != null) {
                handleRequest(request.trim(), writer);
                if (subscribedToEvents) {
                    // Keep the connection open for event delivery until client disconnects.
                    while (reader.readLine() != null) {
                        // Ignore any additional input on the event socket.
                    }
                } else {
                    writer.write("END\n");
                    writer.flush();
                }
            } else {
                writer.write("ERROR;Brak zadania\n");
                writer.write("END\n");
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            HotelServer.clients.remove(this);
            closeSocket();
        }
    }

    public void sendMessage(String message) {
        try {
            writer.write(message + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(String request, BufferedWriter writer) throws IOException {
        if (request.isEmpty()) {
            writer.write("ERROR;Empty request\n");
            return;
        }

        String[] parts = request.split(";");
        String command = parts[0].toUpperCase();

        try {
            switch (command) {
                case "LOGIN" -> handleLogin(parts, writer);
                case "REGISTER" -> handleRegister(parts, writer);
                case "REQUEST_PASSWORD_RESET" -> handleRequestPasswordReset(parts, writer);
                case "RESET_PASSWORD" -> handleResetPassword(parts, writer);
                case "LIST_ROOMS" -> sendRoomList(writer);
                case "SEARCH_ROOMS" -> handleSearchRooms(parts, writer);
                case "ADD_ROOM" -> handleAddRoom(parts, writer);
                case "ADD_ROOM_IMAGE" -> handleAddRoomImage(parts, writer);
                case "GET_ROOM_IMAGES" -> handleGetRoomImages(parts, writer);
                case "DELETE_ROOM_IMAGE" -> handleDeleteRoomImage(parts, writer);
                case "UPDATE_ROOM" -> handleUpdateRoom(parts, writer);
                case "DELETE_ROOM" -> handleDeleteRoom(parts, writer);
                case "RESERVE" -> handleReservation(parts, writer);
                case "LIST_MY_RESERVATIONS" -> handleListMyReservations(parts, writer);
                case "DELETE_RESERVATION" -> handleDeleteReservation(parts, writer);
                case "GENERATE_REPORT" -> handleGenerateReport(parts, writer);
                case "ADMIN_LOGIN" -> handleAdminLogin(parts, writer);
                case "SUBSCRIBE_EVENTS" -> handleSubscribeEvents(writer);

                case "LIST_CLIENTS" -> sendClientList(writer);
                case "DELETE_CLIENT" -> handleDeleteClient(parts, writer);
                case "LIST_RESERVATIONS" -> sendReservationList(writer);
                case "UPDATE_RESERVATION" -> handleUpdateReservation(parts, writer);

                default -> writer.write("ERROR;Unknown command\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Database error: " + e.getMessage() + "\n");
        }
    }

    private void sendClientList(BufferedWriter writer) throws IOException, SQLException {
        List<core.model.Client> clients = databaseManager.getAllClients();
        for (core.model.Client c : clients) {
            writer.write(String.format("CLIENT;%d;%s;%s\n", c.getId(), c.getUsername(), c.getCreatedAt()));
        }
    }

    private void handleDeleteClient(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak ID klienta\n");
            return;
        }
        int id = Integer.parseInt(parts[1]);
        if (databaseManager.deleteClient(id)) {
            writer.write("OK;Klient usunięty\n");
        } else {
            writer.write("ERROR;Nie udało się usunąć klienta\n");
        }
    }

    private void sendReservationList(BufferedWriter writer) throws IOException, SQLException {
        List<core.model.Reservation> reservations = databaseManager.getAllReservations();
        for (core.model.Reservation r : reservations) {
            writer.write(String.format("RESERVATION;%d;%d;%s;%s;%s;%s;%s;%s\n",
                    r.getId(), r.getRoomId(), r.getRoomNumber(), r.getGuestName(), r.getStatus(),
                    r.getCreatedAt(), nullToEmpty(r.getCheckIn()), nullToEmpty(r.getCheckOut())));
        }
    }

    private void handleUpdateReservation(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 3) {
            writer.write("ERROR;Brak parametrów rezerwacji\n");
            return;
        }
        int id = Integer.parseInt(parts[1]);
        String status = parts[2];
        try {
            DatabaseManager.ReservationDetails reservationDetails = databaseManager.getReservationDetails(id);
            if (databaseManager.updateReservationStatus(id, status)) {
                writer.write("OK;Status zaktualizowany pomyślnie\n");
                if (reservationDetails != null) {
                    Room room = databaseManager.getRoomById(reservationDetails.roomId);
                    if (room != null) {
                        EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_UPDATED, room));
                    }
                    broadcastEventToSubscribers("RESERVATION_EVENT;UPDATED;" + reservationDetails.username + ";" + reservationDetails.roomId);
                }
            } else {
                writer.write("ERROR;Nie udało się zaktualizować statusu rezerwacji\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Błąd bazy danych: " + e.getMessage() + "\n");
        }
    }

    private void handleAddRoom(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 11) {
            writer.write("ERROR;Brak wszystkich danych pokoju (w tym udogodnień)\n");
            return;
        }
        try {
            int roomId = databaseManager.addRoomAndReturnId(
                    parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    new java.math.BigDecimal(parts[4]),
                    Boolean.parseBoolean(parts[5]), Boolean.parseBoolean(parts[6]), Boolean.parseBoolean(parts[7]),
                    Boolean.parseBoolean(parts[8]), Boolean.parseBoolean(parts[9]), Boolean.parseBoolean(parts[10]));
            if (roomId > 0) {
                // Fetch the new room and publish event
                Room newRoom = databaseManager.getRoomById(roomId);
                if (newRoom != null) {
                    EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_ADDED, newRoom));
                }
                writer.write("OK;" + roomId + "\n");
            } else {
                writer.write("ERROR;Nie udało się dodać pokoju\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy format liczb\n");
        }
    }

    private void handleAddRoomImage(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 4) {
            writer.write("ERROR;Brak wszystkich danych obrazu pokoju\n");
            return;
        }
        try {
            int roomId = Integer.parseInt(parts[1]);
            String fileName = parts[2];
            byte[] imageData = java.util.Base64.getDecoder().decode(parts[3]);
            boolean success = databaseManager.addRoomImage(roomId, fileName, imageData);
            if (success)
                writer.write("OK;Obraz został dodany do pokoju\n");
            else
                writer.write("ERROR;Nie udało się zapisać obrazu do bazy\n");
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator pokoju\n");
        } catch (IllegalArgumentException e) {
            writer.write("ERROR;Nieprawidłowy format obrazu\n");
        }
    }

    private void handleSubscribeEvents(BufferedWriter writer) throws IOException {
        EventBus.getInstance().subscribe(this);
        subscribedToEvents = true;
        writer.write("OK;SUBSCRIBED\n");
        writer.flush();
    }

    private void handleGetRoomImages(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak ID pokoju\n");
            return;
        }
        try {
            int roomId = Integer.parseInt(parts[1]);
            List<core.model.RoomImage> images = databaseManager.getRoomImages(roomId);
            for (core.model.RoomImage image : images) {
                String encoded = java.util.Base64.getEncoder().encodeToString(image.getData());
                String safeFileName = image.getFileName().replace(";", "_").replace("\n", "_").replace("\r", "_");
                writer.write(String.format("ROOM_IMAGE;%d;%s;%s\n", image.getId(), safeFileName, encoded));
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator pokoju\n");
        }
    }

    private void handleDeleteRoomImage(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak ID obrazu\n");
            return;
        }
        try {
            int imageId = Integer.parseInt(parts[1]);
            if (databaseManager.deleteRoomImage(imageId)) {
                writer.write("OK;Obraz usunięty\n");
            } else {
                writer.write("ERROR;Nie udało się usunąć obrazu\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator obrazu\n");
        }
    }

    private void handleUpdateRoom(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 12) {
            writer.write("ERROR;Brak wszystkich danych do aktualizacji pokoju\n");
            return;
        }
        try {
            int id = Integer.parseInt(parts[1]);
            boolean updated = databaseManager.updateRoom(
                    id,
                    parts[2],
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    new java.math.BigDecimal(parts[5]),
                    Boolean.parseBoolean(parts[6]),
                    Boolean.parseBoolean(parts[7]),
                    Boolean.parseBoolean(parts[8]),
                    Boolean.parseBoolean(parts[9]),
                    Boolean.parseBoolean(parts[10]),
                    Boolean.parseBoolean(parts[11]));
            if (updated) {
                // Fetch updated room and publish event
                Room updatedRoom = databaseManager.getRoomById(id);
                if (updatedRoom != null) {
                    EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_UPDATED, updatedRoom));
                }
                writer.write("OK;Pokój zaktualizowany pomyślnie\n");
            } else {
                writer.write("ERROR;Nie udało się zaktualizować pokoju\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy format danych pokoju\n");
        }
    }

    private void handleDeleteRoom(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak ID pokoju\n");
            return;
        }
        try {
            int id = Integer.parseInt(parts[1]);
            if (databaseManager.hasActiveReservationsForRoom(id)) {
                writer.write("ERROR;Nie można usunąć pokoju, który ma aktywne rezerwacje\n");
                return;
            }
            // Fetch room before deletion for event
            Room deletedRoom = databaseManager.getRoomById(id);
            if (databaseManager.deleteRoom(id)) {
                if (deletedRoom != null) {
                    EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_DELETED, deletedRoom));
                }
                writer.write("OK;Pokój usunięty\n");
            } else {
                writer.write("ERROR;Nie udało się usunąć pokoju\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator pokoju\n");
        }
    }

    private void handleAdminLogin(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;ADMIN_LOGIN wymaga loginu i hasła\n");
            return;
        }
        if (databaseManager.authenticateAdmin(parts[1], parts[2]))
            writer.write("OK;Zalogowano jako administrator\n");
        else
            writer.write("ERROR;Błędny login lub hasło administratora\n");
    }

    private void handleGenerateReport(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        ReportGenerator reportGenerator = new ReportGenerator(databaseManager);
        try {
            String reportName = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "raport_rezerwacji";
            LocalDate fromDate = parts.length > 2 && !parts[2].isBlank() ? LocalDate.parse(parts[2]) : null;
            LocalDate toDate = parts.length > 3 && !parts[3].isBlank() ? LocalDate.parse(parts[3]) : null;

            if ((fromDate == null) != (toDate == null)) {
                writer.write("ERROR;Podaj datę początkową i końcową raportu\n");
                return;
            }
            if (fromDate != null && !toDate.isAfter(fromDate)) {
                writer.write("ERROR;Data końcowa raportu musi być późniejsza niż początkowa\n");
                return;
            }

            Path reportFile = reportGenerator.generateReservationReport(
                    Path.of(sanitizeReportFileName(reportName)),
                    fromDate,
                    toDate);
            writer.write("OK;Report generated;" + reportFile.toAbsolutePath() + "\n");
        } catch (IOException e) {
            writer.write("ERROR;Report generation failed: " + e.getMessage() + "\n");
        } catch (DateTimeParseException e) {
            writer.write("ERROR;Nieprawidłowy format daty raportu (użyj RRRR-MM-DD)\n");
        }
    }

    private String sanitizeReportFileName(String reportName) {
        String safeName = reportName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.isBlank()) {
            safeName = "raport_rezerwacji";
        }
        if (!safeName.toLowerCase().endsWith(".txt")) {
            safeName += ".txt";
        }
        return safeName;
    }

    private void sendRoomList(BufferedWriter writer) throws IOException, SQLException {
        List<Room> rooms = databaseManager.getAvailableRooms();
        for (Room room : rooms) {
            writer.write(String.format("ROOM;%d;%s;%d;%d;%s;%b;%b;%b;%b;%b;%b\n",
                    room.getId(), room.getNumber(), room.getCapacity(), room.getBedCount(),
                    room.getPrice().toPlainString(),
                    room.isReserved(), room.isHasFridge(), room.isHasKitchenette(), room.isHasBalcony(),
                    room.isHasTv(), room.isHasTable()));
        }
    }

    private void handleSearchRooms(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        RoomSearchFilter filter = new RoomSearchFilter();
        try {
            if (parts.length > 1 && !parts[1].isEmpty() && !"-1".equals(parts[1])) {
                filter.setMinCapacity(Integer.parseInt(parts[1]));
            }
            if (parts.length > 2 && !parts[2].isEmpty() && !"-1".equals(parts[2])) {
                filter.setMaxPrice(new BigDecimal(parts[2]));
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowe parametry wyszukiwania\n");
            return;
        }
        if (parts.length > 3) {
            filter.setOnlyAvailable("1".equals(parts[3]) || "true".equalsIgnoreCase(parts[3]));
        }
        try {
            if (parts.length > 4 && !parts[4].isBlank())
                filter.setCheckIn(LocalDate.parse(parts[4]));
            if (parts.length > 5 && !parts[5].isBlank())
                filter.setCheckOut(LocalDate.parse(parts[5]));
        } catch (DateTimeParseException e) {
            writer.write("ERROR;Nieprawidłowy format daty wyszukiwania (użyj RRRR-MM-DD)\n");
            return;
        }
        if (parts.length > 6)
            filter.setRequireFridge("1".equals(parts[6]));
        if (parts.length > 7)
            filter.setRequireKitchenette("1".equals(parts[7]));
        if (parts.length > 8)
            filter.setRequireBalcony("1".equals(parts[8]));
        if (parts.length > 9)
            filter.setRequireTv("1".equals(parts[9]));
        if (parts.length > 10)
            filter.setRequireTable("1".equals(parts[10]));

        List<Room> rooms = databaseManager.searchRooms(filter);
        for (Room room : rooms) {
            writer.write(String.format("ROOM;%d;%s;%d;%d;%s;%b;%b;%b;%b;%b;%b\n",
                    room.getId(), room.getNumber(), room.getCapacity(), room.getBedCount(),
                    room.getPrice().toPlainString(),
                    room.isReserved(), room.isHasFridge(), room.isHasKitchenette(), room.isHasBalcony(),
                    room.isHasTv(), room.isHasTable()));
        }
    }

    private void handleReservation(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 5) {
            writer.write("ERROR;RESERVE wymaga: ROOM_ID;USERNAME;CHECK_IN;CHECK_OUT\n");
            return;
        }
        try {
            int roomId = Integer.parseInt(parts[1]);
            String username = parts[2];
            LocalDate checkIn = LocalDate.parse(parts[3]);
            LocalDate checkOut = LocalDate.parse(parts[4]);

            Integer clientId = databaseManager.getClientIdByUsername(username);
            if (clientId == null) {
                writer.write("ERROR;Nie znaleziono klienta\n");
                return;
            }

            if (databaseManager.reserveRoom(roomId, clientId, username, checkIn, checkOut)) {
                writer.write("OK;Rezerwacja złożona — oczekuje na akceptację\n");
                // Publish room reserved event so room lists update in UIs
                core.model.Room room = databaseManager.getRoomById(roomId);
                if (room != null) {
                    EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_RESERVED, room));
                }
                // Notify subscribed clients/admins to refresh reservation lists
                broadcastEventToSubscribers("RESERVATION_EVENT;ADDED;" + username + ";" + roomId);
            } else {
                writer.write("ERROR;Nie udało się zarezerwować pokoju (zajęty lub nieprawidłowe daty)\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator pokoju\n");
        } catch (DateTimeParseException e) {
            writer.write("ERROR;Nieprawidłowy format daty (użyj RRRR-MM-DD)\n");
        }
    }

    private void handleListMyReservations(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak nazwy użytkownika\n");
            return;
        }
        List<core.model.Reservation> reservations = databaseManager.getReservationsByUsername(parts[1]);
        for (core.model.Reservation r : reservations) {
            writer.write(String.format("RESERVATION;%d;%d;%s;%s;%s;%s;%s;%s\n",
                    r.getId(), r.getRoomId(), r.getRoomNumber(), r.getGuestName(), r.getStatus(),
                    r.getCreatedAt(), nullToEmpty(r.getCheckIn()), nullToEmpty(r.getCheckOut())));
        }
    }

    private void handleDeleteReservation(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length < 2) {
            writer.write("ERROR;Brak ID rezerwacji\n");
            return;
        }
        try {
            int id = Integer.parseInt(parts[1]);
            DatabaseManager.ReservationDetails reservationDetails = databaseManager.getReservationDetails(id);
            if (reservationDetails == null) {
                writer.write("ERROR;Nie znaleziono rezerwacji\n");
                return;
            }
            if (databaseManager.deleteReservation(id)) {
                writer.write("OK;Rezerwacja usunięta\n");
                Room room = databaseManager.getRoomById(reservationDetails.roomId);
                if (room != null) {
                    EventBus.getInstance().publish(new RoomEvent(RoomEvent.EventType.ROOM_UPDATED, room));
                }
                broadcastEventToSubscribers("RESERVATION_EVENT;DELETED;" + reservationDetails.username + ";" + reservationDetails.roomId);
            } else {
                writer.write("ERROR;Nie udało się usunąć rezerwacji\n");
            }
        } catch (NumberFormatException e) {
            writer.write("ERROR;Nieprawidłowy identyfikator rezerwacji\n");
        } catch (SQLException e) {
            writer.write("ERROR;Błąd bazy danych: " + e.getMessage() + "\n");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void handleLogin(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;LOGIN requires username and password\n");
            return;
        }
        if (databaseManager.authenticateClient(parts[1], parts[2])) {
            Integer clientId = databaseManager.getClientIdByUsername(parts[1]);
            writer.write("OK;Login successful;" + clientId + "\n");
        } else {
            writer.write("ERROR;Invalid username or password\n");
        }
    }

    private void handleRegister(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 4) {
            writer.write("ERROR;REGISTER requires username, password, and email\n");
            return;
        }
        try {
            String username = parts[1];
            String password = parts[2];
            String email = parts[3];

            EmailService emailService = new EmailService();
            String confirmationToken = emailService.generateConfirmationToken();

            if (databaseManager.registerClient(username, password, email, confirmationToken)) {
                System.out.println("User registered: " + username + " (" + email + ")");
                emailService.sendConfirmationEmail(email, username);
                writer.write("OK;Registration successful. Check your email to confirm your account.\n");
                // Notify subscribed admin clients about new client for dynamic lists
                broadcastEventToSubscribers("CLIENT_EVENT;ADDED;" + username);
            } else {
                writer.write("ERROR;Username already exists\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Registration failed: " + e.getMessage() + "\n");
        }
    }

    private void handleRequestPasswordReset(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 2) {
            writer.write("ERROR;REQUEST_PASSWORD_RESET requires username\n");
            return;
        }
        try {
            String username = parts[1];
            String email = databaseManager.getEmailByUsername(username);

            if (email == null) {
                writer.write("ERROR;User not found\n");
                return;
            }

            EmailService emailService = new EmailService();
            String resetToken = emailService.generatePasswordResetCode();
            java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusHours(1);

            if (databaseManager.requestPasswordReset(username, email, resetToken, expiresAt)) {
                emailService.sendPasswordResetEmail(email, username, resetToken);
                writer.write("OK;Password reset email sent to " + email + "\n");
            } else {
                writer.write("ERROR;Failed to send reset email\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Password reset request failed: " + e.getMessage() + "\n");
        }
    }

    private void handleResetPassword(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;RESET_PASSWORD requires token and new password\n");
            return;
        }
        try {
            String resetToken = parts[1];
            String newPassword = parts[2];

            if (databaseManager.resetPassword(resetToken, newPassword)) {
                writer.write("OK;Password reset successfully. You can now login.\n");
            } else {
                writer.write("ERROR;Invalid or expired reset token\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Password reset failed: " + e.getMessage() + "\n");
        }
    }

    private void closeSocket() {
        if (subscribedToEvents) {
            EventBus.getInstance().unsubscribe(this);
            subscribedToEvents = false;
        }
        try {
            clientSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void broadcastEventToSubscribers(String message) {
        for (ClientHandler ch : HotelServer.clients) {
            try {
                if (ch.subscribedToEvents) {
                    ch.sendMessage(message);
                }
            } catch (Exception e) {
                System.err.println("Failed to send broadcast to a subscriber: " + e.getMessage());
            }
        }
    }
}
