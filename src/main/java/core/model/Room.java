package core.model;

import java.math.BigDecimal;

public class Room {
    private final int id;
    private final String number;
    private final int capacity;
    private final BigDecimal price;

    public Room(int id, String number, int capacity, BigDecimal price) {
        this.id = id;
        this.number = number;
        this.capacity = capacity;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public int getCapacity() {
        return capacity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return number + " (" + capacity + " places) - " + price + " PLN";
    }
}
