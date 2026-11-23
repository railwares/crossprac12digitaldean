package ua.edu.digitaldean.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.edu.digitaldean.model.StudentId;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestFilesTest {
    @TempDir
    Path directory;

    private static RequestFileRow sample() {
        return new RequestFileRow("REQ-001", new StudentId("ST-2025-0042"), "uk", 1, "Примітка");
    }

    @Test
    void readsTheApplicationSeedResource() throws Exception {
        List<RequestFileRow> rows = new RequestFiles().readResource();
        RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
        var results = processor.processAll(rows.stream().map(RequestFileRow::toRequest).toList());
        assertAll(
                () -> assertEquals(3, rows.size()),
                () -> assertEquals(List.of("REQ-003", "REQ-002", "REQ-006"),
                        rows.stream().map(RequestFileRow::requestId).toList()),
                () -> assertEquals("Перший рядок\nДругий рядок", rows.get(2).note()),
                () -> assertEquals(new BigDecimal("240.00"), processor.totalFee(results)));
    }

    @Test
    void preservesRowsAfterDiskRoundTrip() throws Exception {
        RequestFiles files = new RequestFiles();
        Path target = directory.resolve("дані з пробілом/requests.json");
        List<RequestFileRow> expected = List.of(sample());
        files.write(target, "json", expected);
        assertEquals(expected, files.read(target, "json"));
    }

    @Test
    void refusesToOverwriteAnExistingTarget() throws Exception {
        Path target = directory.resolve("existing.json");
        byte[] original = "КОНТРОЛЬ".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);
        assertThrows(IOException.class,
                () -> new RequestFiles().write(target, "json", List.of(sample())));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void rejectsDuplicateKeysBeforeCreatingTheTarget() {
        Path target = directory.resolve("duplicate.json");
        assertThrows(ExchangeFormatException.class,
                () -> new RequestFiles().write(target, "json", List.of(sample(), sample())));
        assertFalse(Files.exists(target));
    }

    @Test
    void acceptsCsvContainingOnlyTheHeader() throws Exception {
        Path target = directory.resolve("empty.csv");
        Files.writeString(target, "requestId,studentId,language,copies,note\n", StandardCharsets.UTF_8);
        assertTrue(new RequestFiles().read(target, "csv").isEmpty());
    }

    @Test
    void rejectsMalformedUtf8() throws Exception {
        Path target = directory.resolve("malformed.json");
        Files.write(target, new byte[]{(byte) 0xC3, 0x28});
        assertThrows(IOException.class, () -> new RequestFiles().read(target, "json"));
    }
}