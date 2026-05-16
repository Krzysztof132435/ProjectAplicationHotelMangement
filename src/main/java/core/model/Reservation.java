package core.model;

public class Reservation {
    private final int id;
    private final int roomId;
    private final String guestName;
    private final String status;

    public Reservation(int id, int roomId, String guestName, String status) {
        this.id = id;
        this.roomId = roomId;
        this.guestName = guestName;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getStatus() {
        return status;
    }
}
