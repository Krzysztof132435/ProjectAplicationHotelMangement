package server.network;

import core.network.NetworkConfig;
import server.database.DatabaseManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HotelServer {
    private static final int THREAD_POOL_SIZE = 100;

    public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private final DatabaseManager databaseManager;
    private final ExecutorService executorService;
    private final String bindAddress;
    private final int port;

    public HotelServer(DatabaseManager databaseManager, String bindAddress, int port) {
        this.databaseManager = databaseManager;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(bindAddress, port));
            System.out.println("HotelServer listening on " + bindAddress + ":" + port);
            System.out.println("Clients in the same network should connect to this laptop IP, for example: "
                    + getLanAddress() + ":" + port);
            while (!executorService.isShutdown()) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket, databaseManager));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        System.out.println("Shutting down server executor...");
        executorService.shutdownNow();
    }

    public static void main(String[] args) {
        String bindAddress = NetworkConfig.resolveBindAddress(args);
        int port = NetworkConfig.resolveBindPort(args);
        String jdbcUrl = System.getenv().getOrDefault("HOTEL_DB_URL", "jdbc:postgresql://localhost:5432/hotel_db");
        String username = System.getenv().getOrDefault("HOTEL_DB_USER", "hotel_user");
        String password = System.getenv().getOrDefault("HOTEL_DB_PASSWORD", "hotel_pass");

        System.out.println("Starting HotelServer with DB URL: " + jdbcUrl);
        System.out.println("Using DB user: " + username);

        DatabaseManager dbManager = new DatabaseManager(jdbcUrl, username, password);
        try {
            dbManager.initializeSchema();
            System.out.println("Database schema initialized successfully");
        } catch (SQLException e) {
            System.out.println("Warning: Database schema initialization failed: " + e.getMessage());
            System.out.println("Server will continue, but schema may not be up to date");
        }
        new HotelServer(dbManager, bindAddress, port).start();
    }

    private String getLanAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            return "<IP_LAPTOPA>";
        }
    }
}
