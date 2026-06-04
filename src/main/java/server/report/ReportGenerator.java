package server.report;

import server.database.DatabaseManager;
import server.database.DatabaseManager.ReservationReportRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ReportGenerator {
    private final DatabaseManager databaseManager;

    public ReportGenerator(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Path generateReservationReport(Path targetPath) throws IOException, SQLException {
        return generateReservationReport(targetPath, null, null);
    }

    public Path generateReservationReport(Path targetPath, LocalDate fromDate, LocalDate toDate) throws IOException, SQLException {
        List<ReservationReportRow> rows = databaseManager.getReservationReportRows(fromDate, toDate);
        BigDecimal totalPrice = rows.stream()
                .filter(row -> "ACCEPTED".equalsIgnoreCase(row.getStatus()))
                .map(row -> calculateReservationPrice(row, fromDate, toDate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Files.createDirectories(targetPath.toAbsolutePath().getParent() == null ? Path.of(".")
                : targetPath.toAbsolutePath().getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(targetPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            String header = "Hotel Reservation Report";
            String separator = "--------------------------------------------------------------------------------";
            writer.write(header);
            writer.newLine();
            writer.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.newLine();
            if (fromDate != null && toDate != null) {
                writer.write("Date range: " + fromDate + " - " + toDate);
                writer.newLine();
            }
            writer.write(separator);
            writer.newLine();
            writer.write(String.format("%-5s %-10s %-10s %-20s %-12s %-12s %-8s %-10s %-12s %-20s",
                    "ID", "ROOM", "STATUS", "GUEST", "CHECK_IN", "CHECK_OUT", "CAP", "PRICE", "VALUE", "CREATED_AT"));
            writer.newLine();
            writer.write(separator);
            writer.newLine();
            for (ReservationReportRow row : rows) {
                BigDecimal reservationPrice = calculateReservationPrice(row, fromDate, toDate);
                writer.write(String.format("%-5d %-10s %-10s %-20s %-12s %-12s %-8d %-10s %-12s %-20s",
                        row.getReservationId(),
                        row.getRoomNumber(),
                        row.getStatus(),
                        row.getGuestName(),
                        formatDate(row.getCheckIn()),
                        formatDate(row.getCheckOut()),
                        row.getCapacity(),
                        row.getPrice().toPlainString(),
                        reservationPrice.toPlainString(),
                        row.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
                writer.newLine();
            }
            writer.write(separator);
            writer.newLine();
            writer.write("Total reservations: " + rows.size());
            writer.newLine();
            writer.write("Total price: " + totalPrice.toPlainString());
            writer.newLine();
        }

        return targetPath.toAbsolutePath();
    }

    private BigDecimal calculateReservationPrice(ReservationReportRow row, LocalDate fromDate, LocalDate toDate) {
        LocalDate checkIn = row.getCheckIn();
        LocalDate checkOut = row.getCheckOut();
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return row.getPrice();
        }

        LocalDate effectiveStart = fromDate != null && fromDate.isAfter(checkIn) ? fromDate : checkIn;
        LocalDate effectiveEnd = toDate != null && toDate.isBefore(checkOut) ? toDate : checkOut;
        long nights = Math.max(0, ChronoUnit.DAYS.between(effectiveStart, effectiveEnd));
        return row.getPrice().multiply(BigDecimal.valueOf(nights));
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : date.toString();
    }
}
