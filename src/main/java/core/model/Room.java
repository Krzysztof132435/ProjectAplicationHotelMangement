package core.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Room {
    private int id;
    private String number;
    private int capacity;
    private int bedCount;
    private BigDecimal price;
    private List<RoomImage> images = new ArrayList<>();
    private boolean imagesLoaded = false;

    private boolean isReserved;
    private boolean hasFridge;
    private boolean hasKitchenette;
    private boolean hasBalcony;
    private boolean hasTv;
    private boolean hasTable;

    public Room(int id, String number, int capacity, BigDecimal price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) {
        this(id, number, capacity, capacity, price, isReserved, hasFridge, hasKitchenette,
                hasBalcony, hasTv, hasTable);
    }

    public Room(int id, String number, int capacity, int bedCount, BigDecimal price,
            boolean isReserved, boolean hasFridge, boolean hasKitchenette,
            boolean hasBalcony, boolean hasTv, boolean hasTable) {
        this.id = id;
        this.number = number;
        this.capacity = capacity;
        this.bedCount = bedCount;
        this.price = price;
        this.isReserved = isReserved;
        this.hasFridge = hasFridge;
        this.hasKitchenette = hasKitchenette;
        this.hasBalcony = hasBalcony;
        this.hasTv = hasTv;
        this.hasTable = hasTable;
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

    public int getBedCount() {
        return bedCount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isReserved() {
        return isReserved;
    }

    public boolean isHasFridge() {
        return hasFridge;
    }

    public boolean isHasKitchenette() {
        return hasKitchenette;
    }

    public boolean isHasBalcony() {
        return hasBalcony;
    }

    public boolean isHasTv() {
        return hasTv;
    }

    public boolean isHasTable() {
        return hasTable;
    }

    public List<RoomImage> getImages() {
        return images;
    }

    public void setImages(List<RoomImage> images) {
        this.images = images;
    }

    public boolean isImagesLoaded() {
        return imagesLoaded;
    }

    public void setImagesLoaded(boolean imagesLoaded) {
        this.imagesLoaded = imagesLoaded;
    }

    public String getAmenitiesText() {
        StringBuilder sb = new StringBuilder();
        if (hasFridge)
            sb.append("Lodówka, ");
        if (hasKitchenette)
            sb.append("Aneks, ");
        if (hasBalcony)
            sb.append("Balkon, ");
        if (hasTv)
            sb.append("TV, ");
        if (hasTable)
            sb.append("Stół, ");

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
            return sb.toString();
        }
        return "Brak";
    }

    @Override
    public String toString() {
        return String.format("Pokój %s | %d os. | %s zł | %s | %s",
                number, capacity, price.toPlainString(),
                isReserved ? "zajęty" : "wolny",
                getAmenitiesText());
    }
}
