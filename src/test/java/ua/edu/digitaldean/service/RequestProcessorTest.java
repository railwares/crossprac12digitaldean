package ua.edu.digitaldean.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestProcessorTest {
    private static TranscriptRequest request(String id, int copies) {
        return new TranscriptRequest(id, new StudentId("ST-2025-0042"), "uk", copies);
    }

    @Test
    void appliesTheStandardFeePolicy() {
        RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
        TranscriptRequest request = request("REQ-001", 2);
        ProcessingResult result = processor.process(request);
        assertEquals(request.summary(), result.summary());
        assertEquals(new BigDecimal("80.00"), result.fee());
    }

    @Test
    void usesTheInjectedFeePolicy() {
        RequestProcessor processor = new RequestProcessor(value -> new BigDecimal("7.50"));
        assertEquals(new BigDecimal("7.50"), processor.process(request("REQ-001", 1)).fee());
    }

    @Test
    void batchPreservesOrderAndIsUnmodifiable() {
        RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
        TranscriptRequest first = request("REQ-003", 2);
        TranscriptRequest second = request("REQ-001", 1);
        List<ProcessingResult> results = processor.processAll(List.of(first, second));
        assertAll(
                () -> assertEquals(List.of(first.summary(), second.summary()),
                        results.stream().map(ProcessingResult::summary).toList()),
                () -> assertThrows(UnsupportedOperationException.class, results::clear));
    }

    @Test
    void emptyTotalHasTwoDecimalPlaces() {
        RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
        assertEquals(new BigDecimal("0.00"), processor.totalFee(List.of()));
    }

    @Test
    void sumsDecimalValuesExactly() {
        RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
        List<ProcessingResult> results = List.of(
                new ProcessingResult("A", new BigDecimal("0.10")),
                new ProcessingResult("B", new BigDecimal("0.20")));
        assertEquals(new BigDecimal("0.30"), processor.totalFee(results));
    }

    @Test
    void rejectsNullPolicyAsAnApiPrecondition() {
        assertThrows(NullPointerException.class, () -> new RequestProcessor(null));
    }
}