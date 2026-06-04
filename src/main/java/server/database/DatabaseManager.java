package server.database;

import core.model.Room;
import core.model.RoomSearchFilter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("sslmode", "disable");
        return DriverManager.getConnection(jdbcUrl, props);
    }

    public void initializeSchema() throws SQLException {
        try (Connection conn = getConnection();
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE reservations ADD COLUMN IF NOT EXISTS client_id INT REFERENCES clients(id) ON DELETE SET NULL");
            stmt.executeUpdate("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS check_in DATE");
            stmt.executeUpdate("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS check_out DATE");
        }
    }

    public List<Room> getAvailableRooms() throws SQLException {
        List<Room> rooms = new java.util.ArrayList<>();

        String sql = "SELECT id, number, capacity, bed_count, price, is_reserved, has_fridge, has_kitchenette, has_balcony, has_tv, has_table FROM rooms";

        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                java.sql.ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                rooms.add(new Room(
                        rs.getInt("id"),
                        rs.getString("number"),
                        rs.getInt("capacity"),
                        rs.getInt("bed_count"),
                        rs.getBigDecimal("price"),
                        rs.getBoolean("is_reserved"),
                        rs.getBoolean("has_fridge"),
                        rs.getBoolean("has_kitchenette"),
                        rs.getBoolean("has_balcony"),
                        rs.getBoolean("has_tv"),
                        rs.getBoolean("has_table")));
            }
        }
        return rooms;
    }

    public Integer getClientIdByUsername(String username) throws SQLException {
        String sql = "SELECT id FROM clients WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    public List<Room> searchRooms(RoomSearchFilter filter) throws SQLException {
        boolean hasDateRange = filter.getCheckIn() != null
                && filter.getCheckOut() != null
                && filter.getCheckOut().isAfter(filter.getCheckIn());
        String overlapCondition = "SELECT 1 FROM reservations res " +
                "WHERE res.room_id = rooms.id " +
                "AND res.status NOT IN ('REJECTED', 'CANCELLED') " +
                "AND res.check_in IS NOT NULL AND res.check_out IS NOT NULL " +
                "AND res.check_in < ? AND res.check_out > ?";

        StringBuilder sql = new StringBuilder("SELECT id, number, capacity, bed_count, price, ");
        List<Object> params = new ArrayList<>();

        if (hasDateRange) {
            sql.append("(EXISTS (").append(overlapCondition).append(")) AS is_reserved, ");
            params.add(filter.getCheckOut());
            params.add(filter.getCheckIn());
        } else {
            sql.append("is_reserved, ");
        }

        sql.append("has_fridge, has_kitchenette, has_balcony, has_tv, has_table FROM rooms WHERE 1=1");

        if (filter.isOnlyAvailable()) {
            if (hasDateRange) {
                sql.append(" AND NOT EXISTS (").append(overlapCondition).append(")");
                params.add(filter.getCheckOut());
                params.add(filter.getCheckIn());
            } else {
                sql.append(" AND is_reserved = FALSE");
            }
        }
        if (filter.getMinCapacity() > 0) {
            sql.append(" AND capacity >= ?");
            params.add(filter.getMinCapacity());
        }
        if (filter.getMaxPrice() != null) {
            sql.append(" AND price <= ?");
            params.add(filter.getMaxPrice());
        }
        if (filter.isRequireFridge()) {
            sql.append(" AND has_fridge = TRUE");
        }
        if (filter.isRequireKitchenette()) {
            sql.append(" AND has_kitchenette = TRUE");
        }
        if (filter.isRequireBalcony()) {
            sql.append(" AND has_balcony = TRUE");
        }
        if (filter.isRequireTv()) {
            sql.append(" AND has_tv = TRUE");
        }
        if (filter.isRequireTable()) {
            sql.append(" AND has_table = TRUE");
        }
        sql.append(" ORDER BY number");

        List<Room> rooms = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof BigDecimal) {
                    stmt.setBigDecimal(i + 1, (BigDecimal) param);
                } else if (param instanceof LocalDate) {
                    stmt.setDate(i + 1, Date.valueOf((LocalDate) param));
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rooms.add(mapRoom(rs));
                }
            }
        }
        return rooms;
    }

    public boolean reserveRoom(int roomId, int clientId, String guestName,
            LocalDate checkIn, LocalDate checkOut) throws SQLException {
        if (checkOut == null || checkIn == null || !checkOut.isAfter(checkIn)) {
            return false;
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!isRoomAvailableForDates(conn, roomId, checkIn, checkOut)) {
                    conn.rollback();
                    return false;
                }

                String insert = "INSERT INTO reservations (room_id, client_id, guest_name, status, check_in, check_out) "
                        +
                        "VALUES (?, ?, ?, 'PENDING', ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insert)) {
                    stmt.setInt(1, roomId);
                    stmt.setInt(2, clientId);
                    stmt.setString(3, guestName);
                    stmt.setDate(4, Date.valueOf(checkIn));
                    stmt.setDate(5, Date.valueOf(checkOut));
                    if (stmt.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private boolean isRoomAvailableForDates(Connection conn, int roomId,
            LocalDate checkIn, LocalDate checkOut) throws SQLException {
        String roomSql = "SELECT 1 FROM rooms WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(roomSql)) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
            }
        }

        String overlapSql = "SELECT COUNT(*) FROM reservations " +
                "WHERE room_id = ? AND status NOT IN ('REJECTED', 'CANCELLED') " +
                "AND check_in IS NOT NULL AND check_out IS NOT NULL " +
                "AND check_in < ? AND check_out > ?";
        try (PreparedStatement stmt = conn.prepareStatement(overlapSql)) {
            stmt.setInt(1, roomId);
            stmt.setDate(2, Date.valueOf(checkOut));
            stmt.setDate(3, Date.valueOf(checkIn));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<core.model.Reservation> getReservationsByUsername(String username) throws SQLException {
        List<core.model.Reservation> list = new ArrayList<>();
        String sql = "SELECT r.id, r.room_id, rm.number AS room_number, r.guest_name, r.status, r.created_at, " +
                "r.check_in, r.check_out " +
                "FROM reservations r " +
                "JOIN rooms rm ON r.room_id = rm.id " +
                "JOIN clients c ON r.client_id = c.id " +
                "WHERE c.username = ? " +
                "ORDER BY r.check_in DESC NULLS LAST, r.created_at DESC";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReservation(rs));
                }
            }
        }
        return list;
    }

    private Room mapRoom(ResultSet rs) throws SQLException {
        return new Room(
                rs.getInt("id"),
                rs.getString("number"),
                rs.getInt("capacity"),
                rs.getInt("bed_count"),
                rs.getBigDecimal("price"),
                rs.getBoolean("is_reserved"),
                rs.getBoolean("has_fridge"),
                rs.getBoolean("has_kitchenette"),
                rs.getBoolean("has_balcony"),
                rs.getBoolean("has_tv"),
                rs.getBoolean("has_table"));
    }

    private core.model.Reservation mapReservation(ResultSet rs) throws SQLException {
        Date checkIn = rs.getDate("check_in");
        Date checkOut = rs.getDate("check_out");
        return new core.model.Reservation(
                rs.getInt("id"),
                rs.getInt("room_id"),
                rs.getString("room_number"),
                rs.getString("guest_name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toString(),
                checkIn != null ? checkIn.toLocalDate().toString() : null,
                checkOut != null ? checkOut.toLocalDate().toString() : null);
    }

    public List<ReservationReportRow> getReservationReportRows() throws SQLException {
        return getReservationReportRows(null, null);
    }

    public List<ReservationReportRow> getReservationReportRows(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = "SELECT res.id as reservation_id, res.guest_name, res.status, res.created_at, " +
                "res.check_in, res.check_out, " +
                "r.id as room_id, r.number as room_number, r.capacity, r.price " +
                "FROM reservations res " +
                "JOIN rooms r ON res.room_id = r.id ";

        if (fromDate != null && toDate != null) {
            sql += "WHERE res.check_in IS NOT NULL AND res.check_out IS NOT NULL " +
                    "AND res.check_in < ? AND res.check_out > ? ";
        }

        sql += "ORDER BY res.created_at DESC";

        List<ReservationReportRow> rows = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (fromDate != null && toDate != null) {
                stmt.setDate(1, Date.valueOf(toDate));
                stmt.setDate(2, Date.valueOf(fromDate));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date checkIn = rs.getDate("check_in");
                    Date checkOut = rs.getDate("check_out");
                    rows.add(new ReservationReportRow(
                            rs.getInt("reservation_id"),
                            rs.getInt("room_id"),
                            rs.getString("room_number"),
                            rs.getInt("capacity"),
                            rs.getBigDecimal("price"),
                            rs.getString("guest_name"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            checkIn != null ? checkIn.toLocalDate() : null,
                            checkOut != null ? checkOut.toLocalDate() : null));
                }
            }
        }
        return rows;
    }

    public static class ReservationDetails {
        public final int roomId;
        public final String username;

        public ReservationDetails(int roomId, String username) {
            this.roomId = roomId;
            this.username = username;
        }
    }

    public ReservationDetails getReservationDetails(int reservationId) throws SQLException {
        String sql = "SELECT r.room_id, c.username FROM reservations r "
                + "JOIN clients c ON r.client_id = c.id WHERE r.id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reservationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ReservationDetails(rs.getInt("room_id"), rs.getString("username"));
                }
            }
        }
        return null;
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

    public boolean registerClient(String username, String password, String email, String confirmationToken)
            throws SQLException {
        if (clientExists(username)) {
            return false;
        }

        String insert = "INSERT INTO clients (username, password, email, confirmation_token) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.setString(4, confirmationToken);
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

    public boolean authenticateAdmin(String username, String password) throws SQLException {
        String sql = "SELECT password FROM admins WHERE username = ?";
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

    public boolean addRoom(String number, int capacity, int bedCount, java.math.BigDecimal price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) {
        String sql = "INSERT INTO rooms (number, capacity, bed_count, price, is_reserved, has_fridge, has_kitchenette, has_balcony, has_tv, has_table) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, number);
            pstmt.setInt(2, capacity);
            pstmt.setInt(3, bedCount);
            pstmt.setBigDecimal(4, price);
            pstmt.setBoolean(5, isReserved);
            pstmt.setBoolean(6, hasFridge);
            pstmt.setBoolean(7, hasKitchenette);
            pstmt.setBoolean(8, hasBalcony);
            pstmt.setBoolean(9, hasTv);
            pstmt.setBoolean(10, hasTable);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int addRoomAndReturnId(String number, int capacity, int bedCount, java.math.BigDecimal price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) {
        String sql = "INSERT INTO rooms (number, capacity, bed_count, price, is_reserved, has_fridge, has_kitchenette, has_balcony, has_tv, has_table) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, number);
            pstmt.setInt(2, capacity);
            pstmt.setInt(3, bedCount);
            pstmt.setBigDecimal(4, price);
            pstmt.setBoolean(5, isReserved);
            pstmt.setBoolean(6, hasFridge);
            pstmt.setBoolean(7, hasKitchenette);
            pstmt.setBoolean(8, hasBalcony);
            pstmt.setBoolean(9, hasTv);
            pstmt.setBoolean(10, hasTable);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return -1;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean addRoomImage(int roomId, String fileName, byte[] imageData) {
        String sql = "INSERT INTO room_images (room_id, file_name, image_data) VALUES (?, ?, ?)";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            pstmt.setString(2, fileName);
            pstmt.setBytes(3, imageData);
            return pstmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public java.util.List<core.model.RoomImage> getRoomImages(int roomId) throws java.sql.SQLException {
        String sql = "SELECT id, file_name, image_data FROM room_images WHERE room_id = ? ORDER BY id";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                java.util.List<core.model.RoomImage> images = new java.util.ArrayList<>();
                while (rs.next()) {
                    images.add(new core.model.RoomImage(
                            rs.getInt("id"),
                            rs.getString("file_name"),
                            rs.getBytes("image_data")));
                }
                return images;
            }
        }
    }

    public boolean deleteRoomImage(int imageId) {
        String sql = "DELETE FROM room_images WHERE id = ?";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, imageId);
            return pstmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteRoom(int id) {
        if (hasActiveReservationsForRoom(id)) {
            return false;
        }
        String sql = "DELETE FROM rooms WHERE id = ?";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean hasActiveReservationsForRoom(int roomId) {
        String sql = "SELECT COUNT(*) FROM reservations WHERE room_id = ? "
                + "AND status NOT IN ('REJECTED', 'CANCELLED')";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public boolean updateRoom(int id, String number, int capacity, int bedCount, java.math.BigDecimal price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) {
        String sql = "UPDATE rooms SET number = ?, capacity = ?, bed_count = ?, price = ?, is_reserved = ?, has_fridge = ?, has_kitchenette = ?, has_balcony = ?, has_tv = ?, has_table = ? WHERE id = ?";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, number);
            stmt.setInt(2, capacity);
            stmt.setInt(3, bedCount);
            stmt.setBigDecimal(4, price);
            stmt.setBoolean(5, isReserved);
            stmt.setBoolean(6, hasFridge);
            stmt.setBoolean(7, hasKitchenette);
            stmt.setBoolean(8, hasBalcony);
            stmt.setBoolean(9, hasTv);
            stmt.setBoolean(10, hasTable);
            stmt.setInt(11, id);
            return stmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
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
        private final java.time.LocalDate checkIn;
        private final java.time.LocalDate checkOut;

        public ReservationReportRow(int reservationId,
                int roomId,
                String roomNumber,
                int capacity,
                java.math.BigDecimal price,
                String guestName,
                String status,
                java.time.LocalDateTime createdAt,
                java.time.LocalDate checkIn,
                java.time.LocalDate checkOut) {
            this.reservationId = reservationId;
            this.roomId = roomId;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.price = price;
            this.guestName = guestName;
            this.status = status;
            this.createdAt = createdAt;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
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

        public java.time.LocalDate getCheckIn() {
            return checkIn;
        }

        public java.time.LocalDate getCheckOut() {
            return checkOut;
        }
    }

    public java.util.List<core.model.Client> getAllClients() throws java.sql.SQLException {
        java.util.List<core.model.Client> clients = new java.util.ArrayList<>();
        String sql = "SELECT id, username, email, is_confirmed, created_at FROM clients";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                java.sql.ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                clients.add(new core.model.Client(rs.getInt("id"), rs.getString("username"), rs.getString("email"),
                        rs.getBoolean("is_confirmed"), rs.getTimestamp("created_at").toString()));
            }
        }
        return clients;
    }

    public boolean deleteClient(int id) {
        String sql = "DELETE FROM clients WHERE id = ?";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public java.util.List<core.model.Reservation> getAllReservations() throws java.sql.SQLException {
        java.util.List<core.model.Reservation> list = new java.util.ArrayList<>();
        String sql = "SELECT r.id, r.room_id, rm.number AS room_number, r.guest_name, r.status, r.created_at, " +
                "r.check_in, r.check_out " +
                "FROM reservations r JOIN rooms rm ON r.room_id = rm.id ORDER BY r.created_at DESC";
        try (java.sql.Connection conn = getConnection();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                java.sql.ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapReservation(rs));
            }
        }
        return list;
    }

    public boolean updateReservationStatus(int id, String status) {
        String sql = "UPDATE reservations SET status = ? WHERE id = ?";
        java.sql.Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status);
                stmt.setInt(2, id);
                stmt.executeUpdate();
            }

            if ("ACCEPTED".equalsIgnoreCase(status)) {
                String roomSql = "UPDATE rooms SET is_reserved = TRUE WHERE id = (SELECT room_id FROM reservations WHERE id = ?)";
                try (java.sql.PreparedStatement stmtRoom = conn.prepareStatement(roomSql)) {
                    stmtRoom.setInt(1, id);
                    stmtRoom.executeUpdate();
                }
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                String roomSql = "UPDATE rooms SET is_reserved = FALSE WHERE id = (SELECT room_id FROM reservations WHERE id = ?)";
                try (java.sql.PreparedStatement stmtRoom = conn.prepareStatement(roomSql)) {
                    stmtRoom.setInt(1, id);
                    stmtRoom.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (java.sql.SQLException e) {
            if (conn != null)
                try {
                    conn.rollback();
                } catch (Exception ex) {
                }
            return false;
        } finally {
            if (conn != null)
                try {
                    conn.close();
                } catch (Exception ex) {
                }
        }
    }

    public boolean deleteReservation(int reservationId) {
        String selectSql = "SELECT room_id, status FROM reservations WHERE id = ?";
        String deleteSql = "DELETE FROM reservations WHERE id = ?";
        try (java.sql.Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            int roomId;
            String status;
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setInt(1, reservationId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    roomId = rs.getInt("room_id");
                    status = rs.getString("status");
                }
            }

            try (java.sql.PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, reservationId);
                stmt.executeUpdate();
            }

            if (status != null && !"REJECTED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status)) {
                String updateRoomSql = "UPDATE rooms SET is_reserved = FALSE WHERE id = ?";
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(updateRoomSql)) {
                    stmt.setInt(1, roomId);
                    stmt.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean requestPasswordReset(String username, String email, String resetToken,
            java.time.LocalDateTime expiresAt) throws SQLException {
        String sql = "UPDATE clients SET password_reset_token = ?, password_reset_expires = ? WHERE username = ? AND email = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, resetToken);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(expiresAt));
            stmt.setString(3, username);
            stmt.setString(4, email);
            return stmt.executeUpdate() > 0;
        }
    }

    public String getEmailByUsername(String username) throws SQLException {
        String sql = "SELECT email FROM clients WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("email");
                }
                return null;
            }
        }
    }

    public boolean resetPassword(String resetToken, String newPassword) throws SQLException {
        String sql = "UPDATE clients SET password = ?, password_reset_token = NULL, password_reset_expires = NULL WHERE password_reset_token = ? AND password_reset_expires > NOW()";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, resetToken);
            return stmt.executeUpdate() > 0;
        }
    }

    public Room getRoomById(int roomId) {
        String sql = "SELECT id, number, capacity, bed_count, price, is_reserved, has_fridge, has_kitchenette, has_balcony, has_tv, has_table FROM rooms WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Room(
                            rs.getInt("id"),
                            rs.getString("number"),
                            rs.getInt("capacity"),
                            rs.getInt("bed_count"),
                            rs.getBigDecimal("price"),
                            rs.getBoolean("is_reserved"),
                            rs.getBoolean("has_fridge"),
                            rs.getBoolean("has_kitchenette"),
                            rs.getBoolean("has_balcony"),
                            rs.getBoolean("has_tv"),
                            rs.getBoolean("has_table"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
