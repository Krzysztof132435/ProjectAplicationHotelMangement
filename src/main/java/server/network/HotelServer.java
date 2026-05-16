package server.network;

import server.database.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HotelServer {
    private static final int SERVER_PORT = 12345;
    private static final int THREAD_POOL_SIZE = 10;

    private final DatabaseManager databaseManager;
    private final ExecutorService executorService;

    public HotelServer(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void start() {
        System.out.println("HotelServer starting on port " + SERVER_PORT);
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            while (!executorService.isShutdown()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted client: " + clientSocket.getRemoteSocketAddress());
                executorService.submit(new ClientHandler(clientSocket, databaseManager));
            }
        } catch (IOException e) {
            System.err.println("Server socket error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        System.out.println("Shutting down server executor...");
        executorService.shutdownNow();
    }

    public static void main(String[] args) {
        String jdbcUrl = System.getenv().getOrDefault("HOTEL_DB_URL", "jdbc:postgresql://localhost:5432/hotel_db");
        String username = System.getenv().getOrDefault("HOTEL_DB_USER", "hotel_user");
        String password = System.getenv().getOrDefault("HOTEL_DB_PASSWORD", "hotel_pass");

        System.out.println("Starting HotelServer with DB URL: " + jdbcUrl);
        System.out.println("Using DB user: " + username);

        DatabaseManager dbManager = new DatabaseManager(jdbcUrl, username, password);
        new HotelServer(dbManager).start();
    }
}
