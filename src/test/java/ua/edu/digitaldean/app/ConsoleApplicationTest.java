package ua.edu.digitaldean.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.service.ExchangeFormatException;
import ua.edu.digitaldean.service.ExchangeSummary;
import ua.edu.digitaldean.service.InMemoryGateway;
import ua.edu.digitaldean.service.RequestExchangeService;
import ua.edu.digitaldean.service.RequestFileRow;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.StandardFeePolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleApplicationTest {
    private static final class SpyHandler extends Handler {
        private final List<LogRecord> events = new ArrayList<>();
        private boolean closed;

        private SpyHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            events.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static Logger newLogger(SpyHandler handler) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    @Test
    void printsNonEmptySummaryAndLeavesStreamOpen() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            new SummaryPrinter().print(new ExchangeSummary(3, 3, new BigDecimal("240.00"), 3), out);
            out.print("OK");
        }
        String expected = "Прочитано заяв: 3" + System.lineSeparator()
                + "Зареєстровано заяв: 3" + System.lineSeparator()
                + "Сума зборів: 240.00 UAH" + System.lineSeparator()
                + "Заяв у черзі: 3" + System.lineSeparator() + "OK";
        assertEquals(expected, buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void printsZeroSummary() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            new SummaryPrinter().print(new ExchangeSummary(0, 0, new BigDecimal("0.00"), 0), out);
        }
        String expected = "Прочитано заяв: 0" + System.lineSeparator()
                + "Зареєстровано заяв: 0" + System.lineSeparator()
                + "Сума зборів: 0.00 UAH" + System.lineSeparator()
                + "Заяв у черзі: 0" + System.lineSeparator();
        assertEquals(expected, buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void invalidArgumentsMakeZeroGatewayAndLoggerCalls() {
        InMemoryGateway gateway = new InMemoryGateway();
        ConsoleApplication app = new ConsoleApplication(
                new RequestExchangeService(gateway, new RequestProcessor(new StandardFeePolicy())),
                new SummaryPrinter());
        AtomicInteger loggerCalls = new AtomicInteger();
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            int code = app.run(new String[]{"--inspect", "yaml", "x.yaml"}, out, err, () -> {
                loggerCalls.incrementAndGet();
                return newLogger(new SpyHandler());
            });
            assertEquals(2, code);
        }
        assertEquals(0, gateway.readCalls());
        assertEquals(0, gateway.writeCalls());
        assertEquals(0, loggerCalls.get());
        assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
        assertEquals("Помилка: некоректні аргументи." + System.lineSeparator(),
                errBuf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void mapsFormatAndIoFailuresToDistinctConsoleCodes() {
        ExchangeFormatException dataEx = new ExchangeFormatException("Пошкоджені дані");
        InMemoryGateway dataGateway = new InMemoryGateway().withReadFailure(dataEx);
        ConsoleApplication dataApp = new ConsoleApplication(
                new RequestExchangeService(dataGateway, new RequestProcessor(new StandardFeePolicy())),
                new SummaryPrinter());
        SpyHandler h1 = new SpyHandler();
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        ByteArrayOutputStream err1 = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(out1, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(err1, true, StandardCharsets.UTF_8)) {
            assertEquals(3, dataApp.run(new String[0], out, err, () -> newLogger(h1)));
        }
        assertEquals("EXCHANGE_DATA_ERROR", h1.events.getFirst().getMessage());
        assertSame(dataEx, h1.events.getFirst().getThrown());

        IOException ioEx = new IOException("Помилка диска");
        InMemoryGateway ioGateway = new InMemoryGateway()
                .withRows(List.of(new RequestFileRow("REQ-001", new StudentId("ST-2025-0042"), "uk", 1, "OK")))
                .withWriteFailure(ioEx);
        ConsoleApplication ioApp = new ConsoleApplication(
                new RequestExchangeService(ioGateway, new RequestProcessor(new StandardFeePolicy())),
                new SummaryPrinter());
        SpyHandler h2 = new SpyHandler();
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        ByteArrayOutputStream err2 = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(out2, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(err2, true, StandardCharsets.UTF_8)) {
            assertEquals(4, ioApp.run(new String[]{"--seed", "out.json"}, out, err, () -> newLogger(h2)));
        }
        assertEquals("EXCHANGE_IO_ERROR", h2.events.getFirst().getMessage());
        assertSame(ioEx, h2.events.getFirst().getThrown());
    }

    @Test
    void sequentialRunsRemainIndependentAndCloseOwnHandlers() {
        InMemoryGateway gateway = new InMemoryGateway().withRows(List.of());
        ConsoleApplication app = new ConsoleApplication(
                new RequestExchangeService(gateway, new RequestProcessor(new StandardFeePolicy())),
                new SummaryPrinter());
        SpyHandler h1 = new SpyHandler();
        SpyHandler h2 = new SpyHandler();
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            assertEquals(0, app.run(new String[0], out, err, () -> newLogger(h1)));
            assertEquals(0, app.run(new String[0], out, err, () -> newLogger(h2)));
            assertFalse(out.checkError());
        }
        assertEquals(1, h1.events.size());
        assertEquals(1, h2.events.size());
        assertTrue(h1.closed);
        assertTrue(h2.closed);
    }
}