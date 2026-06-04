package core.model;

public class Client {
    private int id;
    private String username;
    private String email;
    private boolean isConfirmed;
    private String createdAt;

    public Client(int id, String username, String email, boolean isConfirmed, String createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.isConfirmed = isConfirmed;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isConfirmed() { return isConfirmed; }
    public String getCreatedAt() { return createdAt; }
}