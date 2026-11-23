package ua.edu.digitaldean.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.edu.digitaldean.model.StudentId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestExchangeServiceTest {
    private static List<RequestFileRow> threeValidRows() {
        return List.of(
                new RequestFileRow("REQ-003", new StudentId("ST-2025-0042"), "uk", 2, "Примітка 1"),
                new RequestFileRow("REQ-002", new StudentId("ST-2025-0007"), "en", 1, "Примітка 2"),
                new RequestFileRow("REQ-006", new StudentId("ST-2025-0099"), "uk", 3, "Примітка 3"));
    }

    @Test
    void processesThreeValidRowsAndPreservesOrder() throws Exception {
        List<RequestFileRow> input = threeValidRows();
        InMemoryGateway gateway = new InMemoryGateway().withRows(input);
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        ExchangeSummary summary = service.execute(
                new LaunchOptions("--seed", "csv", null, "json", Path.of("out.json")));

        assertEquals(new ExchangeSummary(3, 3, new BigDecimal("240.00"), 3), summary);
        assertEquals(1, gateway.readCalls());
        assertEquals(1, gateway.writeCalls());
        assertEquals(input, gateway.lastWritten());
    }

    @Test
    void processesEmptyRowList() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway().withRows(List.of());
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        ExchangeSummary summary = service.execute(
                new LaunchOptions("--resource", "csv", null, null, null));

        assertEquals(new ExchangeSummary(0, 0, new BigDecimal("0.00"), 0), summary);
        assertEquals(0, gateway.writeCalls());
    }

    @Test
    void rejectsDuplicateNormalizedKeysBeforeWriting() {
        List<RequestFileRow> duplicates = List.of(
                new RequestFileRow("req-001", new StudentId("ST-2025-0042"), "uk", 1, "A"),
                new RequestFileRow("REQ-001", new StudentId("ST-2025-0007"), "en", 2, "B"));
        InMemoryGateway gateway = new InMemoryGateway().withRows(duplicates);
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        assertThrows(ExchangeFormatException.class, () -> service.execute(
                new LaunchOptions("--convert", "csv", Path.of("in.csv"), "json", Path.of("out.json"))));
        assertEquals(0, gateway.writeCalls());
    }

    @Test
    void propagatesExactIoExceptionOnReadWithoutWriting() {
        IOException expected = new IOException("Помилка читання");
        InMemoryGateway gateway = new InMemoryGateway().withReadFailure(expected);
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        IOException actual = assertThrows(IOException.class, () -> service.execute(
                new LaunchOptions("--seed", "csv", null, "json", Path.of("out.json"))));
        assertSame(expected, actual);
        assertEquals(0, gateway.writeCalls());
    }

    @Test
    void propagatesExactExchangeFormatExceptionOnRead() {
        ExchangeFormatException expected = new ExchangeFormatException("Помилка формату");
        InMemoryGateway gateway = new InMemoryGateway().withReadFailure(expected);
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        ExchangeFormatException actual = assertThrows(ExchangeFormatException.class, () -> service.execute(
                new LaunchOptions("--resource", "csv", null, null, null)));
        assertSame(expected, actual);
        assertEquals(0, gateway.writeCalls());
    }

    @Test
    void propagatesExactIoExceptionOnWrite() {
        IOException expected = new IOException("Помилка запису");
        InMemoryGateway gateway = new InMemoryGateway()
                .withRows(threeValidRows())
                .withWriteFailure(expected);
        RequestExchangeService service = new RequestExchangeService(
                gateway, new RequestProcessor(new StandardFeePolicy()));

        IOException actual = assertThrows(IOException.class, () -> service.execute(
                new LaunchOptions("--seed", "csv", null, "json", Path.of("out.json"))));
        assertSame(expected, actual);
        assertEquals(1, gateway.writeCalls());
    }
}