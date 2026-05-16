package server.network;

import core.model.Room;
import server.database.DatabaseManager;
import server.report.ReportGenerator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final DatabaseManager databaseManager;

    public ClientHandler(Socket clientSocket, DatabaseManager databaseManager) {
        this.clientSocket = clientSocket;
        this.databaseManager = databaseManager;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
            String request;
            while ((request = reader.readLine()) != null) {
                handleRequest(request.trim(), writer);
                writer.write("END\n");
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Client disconnected or I/O error: " + e.getMessage());
        } finally {
            closeSocket();
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
                case "LIST_ROOMS" -> sendRoomList(writer);
                case "RESERVE" -> handleReservation(parts, writer);
                case "GENERATE_REPORT" -> handleGenerateReport(writer);
                default -> writer.write("ERROR;Unknown command\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Database error: " + e.getMessage() + "\n");
        }
    }

    private void handleGenerateReport(BufferedWriter writer) throws IOException, SQLException {
        ReportGenerator reportGenerator = new ReportGenerator(databaseManager);
        try {
            Path reportFile = reportGenerator.generateReservationReport(Path.of("raport_rezerwacji.txt"));
            writer.write("OK;Report generated;" + reportFile.toAbsolutePath() + "\n");
        } catch (IOException e) {
            writer.write("ERROR;Report generation failed: " + e.getMessage() + "\n");
        }
    }

    private void sendRoomList(BufferedWriter writer) throws IOException, SQLException {
        List<Room> rooms = databaseManager.getAvailableRooms();
        if (rooms.isEmpty()) {
            writer.write("INFO;No rooms available\n");
            return;
        }

        for (Room room : rooms) {
            writer.write(String.format("ROOM;%d;%s;%d;%s\n",
                    room.getId(),
                    room.getNumber(),
                    room.getCapacity(),
                    room.getPrice().toPlainString()));
        }
    }

    private void handleReservation(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;RESERVE requires ROOM_ID and GUEST_NAME\n");
            return;
        }

        int roomId;
        try {
            roomId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            writer.write("ERROR;Invalid room id\n");
            return;
        }

        String guestName = parts[2];
        boolean success = databaseManager.reserveRoom(roomId, guestName);
        if (success) {
            writer.write("OK;Reservation confirmed\n");
        } else {
            writer.write("ERROR;Reservation failed\n");
        }
    }

    private void handleLogin(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;LOGIN requires username and password\n");
            return;
        }

        String username = parts[1];
        String password = parts[2];
        boolean success = databaseManager.authenticateClient(username, password);
        if (success) {
            writer.write("OK;Login successful\n");
        } else {
            writer.write("ERROR;Invalid username or password\n");
        }
    }

    private void handleRegister(String[] parts, BufferedWriter writer) throws IOException, SQLException {
        if (parts.length < 3) {
            writer.write("ERROR;REGISTER requires username and password\n");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        try {
            boolean success = databaseManager.registerClient(username, password);
            if (success) {
                writer.write("OK;Registration successful\n");
            } else {
                writer.write("ERROR;Username already exists\n");
            }
        } catch (SQLException e) {
            writer.write("ERROR;Registration failed: " + e.getMessage() + "\n");
        }
    }

    private void closeSocket() {
        try {
            clientSocket.close();
        } catch (IOException ignored) {
        }
    }
}
