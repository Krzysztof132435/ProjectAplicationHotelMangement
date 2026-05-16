package server.database;

import core.model.Room;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseManager(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public List<Room> getAvailableRooms() throws SQLException {
        String sql = "SELECT r.id, r.number, r.capacity, r.price " +
                "FROM rooms r " +
                "LEFT JOIN reservations res ON r.id = res.room_id AND res.status = 'CONFIRMED' " +
                "WHERE res.id IS NULL " +
                "ORDER BY r.number";

        List<Room> rooms = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rooms.add(new Room(
                        rs.getInt("id"),
                        rs.getString("number"),
                        rs.getInt("capacity"),
                        rs.getBigDecimal("price")));
            }
        }
        return rooms;
    }

    public boolean reserveRoom(int roomId, String guestName) throws SQLException {
        String insert = "INSERT INTO reservations (room_id, guest_name, status) VALUES (?, ?, 'CONFIRMED')";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setInt(1, roomId);
            stmt.setString(2, guestName);
            return stmt.executeUpdate() == 1;
        }
    }

    public List<ReservationReportRow> getReservationReportRows() throws SQLException {
        String sql = "SELECT res.id as reservation_id, res.guest_name, res.status, res.created_at, " +
                "r.id as room_id, r.number as room_number, r.capacity, r.price " +
                "FROM reservations res " +
                "JOIN rooms r ON res.room_id = r.id " +
                "ORDER BY res.created_at DESC";

        List<ReservationReportRow> rows = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.add(new ReservationReportRow(
                        rs.getInt("reservation_id"),
                        rs.getInt("room_id"),
                        rs.getString("room_number"),
                        rs.getInt("capacity"),
                        rs.getBigDecimal("price"),
                        rs.getString("guest_name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }
        return rows;
    }

    public boolean clientExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM clients WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean registerClient(String username, String password) throws SQLException {
        if (clientExists(username)) {
            return false;
        }

        String insert = "INSERT INTO clients (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean authenticateClient(String username, String password) throws SQLException {
        String sql = "SELECT password FROM clients WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    return storedPassword.equals(password);
                }
                return false;
            }
        }
    }

    public static class ReservationReportRow {
        private final int reservationId;
        private final int roomId;
        private final String roomNumber;
        private final int capacity;
        private final java.math.BigDecimal price;
        private final String guestName;
        private final String status;
        private final java.time.LocalDateTime createdAt;

        public ReservationReportRow(int reservationId,
                int roomId,
                String roomNumber,
                int capacity,
                java.math.BigDecimal price,
                String guestName,
                String status,
                java.time.LocalDateTime createdAt) {
            this.reservationId = reservationId;
            this.roomId = roomId;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.price = price;
            this.guestName = guestName;
            this.status = status;
            this.createdAt = createdAt;
        }

        public int getReservationId() {
            return reservationId;
        }

        public int getRoomId() {
            return roomId;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public int getCapacity() {
            return capacity;
        }

        public java.math.BigDecimal getPrice() {
            return price;
        }

        public String getGuestName() {
            return guestName;
        }

        public String getStatus() {
            return status;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
