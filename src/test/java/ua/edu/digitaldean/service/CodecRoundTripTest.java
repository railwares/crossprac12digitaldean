package ua.edu.digitaldean.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.edu.digitaldean.model.StudentId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodecRoundTripTest {
    private static RequestFileRow sample() {
        return new RequestFileRow(
                "REQ-001",
                new StudentId("ST-2025-0042"),
                "uk",
                2,
                "Довідка, \"терміново\"\nШлях: data\\довідки");
    }

    private static Reader fixture(String name) {
        InputStream input = CodecRoundTripTest.class.getResourceAsStream("/test-data/" + name);
        assertNotNull(input, "Тестовий ресурс має бути у classpath.");
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    @Test
    void preservesAllFieldsInCsvJsonRoundTrip() throws Exception {
        List<RequestFileRow> expected = List.of(sample());
        StringWriter csv = new StringWriter();
        new CsvCodec().write(csv, expected);
        List<RequestFileRow> fromCsv = new CsvCodec().read(new StringReader(csv.toString()));
        StringWriter json = new StringWriter();
        new JsonCodec().write(json, fromCsv);
        assertEquals(expected, new JsonCodec().read(new StringReader(json.toString())));
    }

    @Test
    void readsEmptyTestResource() throws Exception {
        try (Reader reader = fixture("empty.json")) {
            assertTrue(new JsonCodec().read(reader).isEmpty());
        }
    }

    @Test
    void writesAnEmptyJsonArray() throws Exception {
        StringWriter writer = new StringWriter();
        new JsonCodec().write(writer, List.of());
        assertEquals("[\n]\n", writer.toString());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ExchangeFormatException.class,
                () -> new JsonCodec().read(new StringReader("[{]")));
    }

    @Test
    void rejectsMalformedCsv() {
        assertThrows(ExchangeFormatException.class,
                () -> new CsvCodec().read(new StringReader("wrong,header\n")));
    }

    @Test
    void returnsAnUnmodifiableList() throws Exception {
        List<RequestFileRow> rows = new JsonCodec().read(new StringReader("[]"));
        assertThrows(UnsupportedOperationException.class, () -> rows.add(sample()));
    }

    @Test
    void rejectsOutOfRangeCopiesFromResource() throws Exception {
        try (Reader reader = fixture("invalid-copies.json")) {
            ExchangeFormatException exception = assertThrows(ExchangeFormatException.class,
                    () -> new JsonCodec().read(reader));
            assertTrue(exception.getCause() instanceof IllegalArgumentException);
        }
    }
}