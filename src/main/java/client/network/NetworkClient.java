package client.network;

import core.model.Room;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class NetworkClient {
    private final String serverHost;
    private final int serverPort;

    public NetworkClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public List<Room> requestAvailableRooms() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write("LIST_ROOMS\n");
            writer.flush();

            List<Room> rooms = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END")) {
                    break;
                }
                if (line.startsWith("ROOM;")) {
                    Room room = parseRoom(line);
                    rooms.add(room);
                }
            }
            return rooms;
        }
    }

    private Room parseRoom(String line) {
        String[] parts = line.split(";");
        int id = Integer.parseInt(parts[1]);
        String number = parts[2];
        int capacity = Integer.parseInt(parts[3]);
        return new Room(id, number, capacity, new java.math.BigDecimal(parts[4]));
    }

    public String login(String username, String password) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write("LOGIN;" + username + ";" + password + "\n");
            writer.flush();

            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END")) {
                    break;
                }
                return response;
            }
            return "ERROR;No response from server";
        }
    }

    public String register(String username, String password) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write("REGISTER;" + username + ";" + password + "\n");
            writer.flush();

            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END")) {
                    break;
                }
                return response;
            }
            return "ERROR;No response from server";
        }
    }
}
