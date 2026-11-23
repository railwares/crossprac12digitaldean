package ua.edu.digitaldean.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterizedExchangeTest {
    private static final StudentId STUDENT = new StudentId("ST-2025-0042");
    private static final String GRADUATION_CAP = "\uD83C\uDF93"; // U+1F393 (поза BMP)

    @ParameterizedTest
    @CsvSource({"1, 40", "5, 200"})
    void acceptsBoundaryCopiesAndComputesBaseFee(int copies, String expectedFee) {
        TranscriptRequest req = new TranscriptRequest("REQ-001", STUDENT, "uk", copies);
        assertEquals(0, new BigDecimal(expectedFee).compareTo(req.baseFee()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsOutOfRangeCopies(int copies) {
        assertThrows(IllegalArgumentException.class,
                () -> new TranscriptRequest("REQ-001", STUDENT, "uk", copies));
    }

    @ParameterizedTest
    @ValueSource(ints = {200})
    void acceptsNoteWith200CodePointsIncludingNonBmp(int length) {
        String note = GRADUATION_CAP + "а".repeat(length - 1);
        assertEquals(200, note.codePointCount(0, note.length()));
        assertTrue(note.length() > 200);
        RequestFileRow row = new RequestFileRow("REQ-001", STUDENT, "uk", 1, note);
        assertEquals(note, row.note());
    }

    @ParameterizedTest
    @ValueSource(ints = {201})
    void rejectsNoteWith201CodePointsIncludingNonBmp(int length) {
        String note = GRADUATION_CAP + "а".repeat(length - 1);
        assertEquals(201, note.codePointCount(0, note.length()));
        assertThrows(IllegalArgumentException.class,
                () -> new RequestFileRow("REQ-001", STUDENT, "uk", 1, note));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]"})
    void readsEmptyJsonArrayAsUnmodifiableEmptyList(String json) throws Exception {
        List<RequestFileRow> rows = new JsonCodec().read(new StringReader(json));
        assertTrue(rows.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> rows.add(new RequestFileRow("REQ-001", STUDENT, "uk", 1, "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {""})
    void rejectsEmptyJsonText(String json) {
        assertThrows(ExchangeFormatException.class,
                () -> new JsonCodec().read(new StringReader(json)));
    }
}