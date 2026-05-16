package server.report;

import server.database.DatabaseManager;
import server.database.DatabaseManager.ReservationReportRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportGenerator {
    private final DatabaseManager databaseManager;

    public ReportGenerator(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Path generateReservationReport(Path targetPath) throws IOException, SQLException {
        List<ReservationReportRow> rows = databaseManager.getReservationReportRows();
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
            writer.write(separator);
            writer.newLine();
            writer.write(String.format("%-5s %-10s %-10s %-20s %-8s %-10s %-20s",
                    "ID", "ROOM", "STATUS", "GUEST", "CAP", "PRICE", "CREATED_AT"));
            writer.newLine();
            writer.write(separator);
            writer.newLine();
            for (ReservationReportRow row : rows) {
                writer.write(String.format("%-5d %-10s %-10s %-20s %-8d %-10s %-20s",
                        row.getReservationId(),
                        row.getRoomNumber(),
                        row.getStatus(),
                        row.getGuestName(),
                        row.getCapacity(),
                        row.getPrice().toPlainString(),
                        row.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
                writer.newLine();
            }
            writer.write(separator);
            writer.newLine();
            writer.write("Total reservations: " + rows.size());
            writer.newLine();
        }

        return targetPath.toAbsolutePath();
    }
}
