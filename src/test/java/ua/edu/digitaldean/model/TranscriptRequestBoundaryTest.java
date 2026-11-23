package ua.edu.digitaldean.model;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranscriptRequestBoundaryTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void acceptsTheInclusiveCopiesBounds(int copies) {
        TranscriptRequest request = new TranscriptRequest(
                "REQ-001", new StudentId("ST-2025-0042"), "uk", copies);
        assertEquals(copies, request.copies());
        assertEquals(0, request.baseFee().compareTo(BigDecimal.valueOf(40L * copies)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsValuesOutsideTheCopiesBounds(int copies) {
        assertThrows(IllegalArgumentException.class, () -> new TranscriptRequest(
                "REQ-001", new StudentId("ST-2025-0042"), "uk", copies));
    }
}