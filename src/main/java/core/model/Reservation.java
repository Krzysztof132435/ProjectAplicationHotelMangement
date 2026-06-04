package core.model;

public class Reservation {
    private final int id;
    private final int roomId;
    private final String roomNumber;
    private final String guestName;
    private final String status;
    private final String createdAt;
    private final String checkIn;
    private final String checkOut;

    public Reservation(int id, int roomId, String roomNumber, String guestName, String status, String createdAt) {
        this(id, roomId, roomNumber, guestName, status, createdAt, null, null);
    }

    public Reservation(int id, int roomId, String roomNumber, String guestName, String status,
                       String createdAt, String checkIn, String checkOut) {
        this.id = id;
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.guestName = guestName;
        this.status = status;
        this.createdAt = createdAt;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public int getId() { return id; }
    public int getRoomId() { return roomId; }
    public String getRoomNumber() { return roomNumber; }
    public String getGuestName() { return guestName; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getCheckIn() { return checkIn; }
    public String getCheckOut() { return checkOut; }

    public String getDateRangeText() {
        if (checkIn != null && checkOut != null) {
            return checkIn + " — " + checkOut;
        }
        return "—";
    }
}
