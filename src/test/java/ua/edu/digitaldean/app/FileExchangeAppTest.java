package ua.edu.digitaldean.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.edu.digitaldean.service.ExchangeFormatException;
import ua.edu.digitaldean.service.RequestFiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExchangeAppTest {
    @TempDir
    Path directory;

    private record RunResult(int code, String output, String error,
                             List<LogRecord> events, int factories, boolean closed) {
    }

    private static final class ProbeHandler extends Handler {
        private final List<LogRecord> events = new ArrayList<>();
        private boolean closed;

        private ProbeHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                events.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingBuffer extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static Logger logger(ProbeHandler handler) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    private static String expected(int count, int total) {
        return "Прочитано заяв: " + count + System.lineSeparator()
                + "Зареєстровано заяв: " + count + System.lineSeparator()
                + "Сума зборів: " + total + ".00 UAH" + System.lineSeparator()
                + "Заяв у черзі: " + count + System.lineSeparator();
    }

    private static RunResult capture(String[] args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        ProbeHandler handler = new ProbeHandler();
        AtomicInteger factories = new AtomicInteger();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            int code = FileExchangeApp.run(args, out, err, () -> {
                factories.incrementAndGet();
                return logger(handler);
            });
            return new RunResult(code, output.toString(StandardCharsets.UTF_8),
                    error.toString(StandardCharsets.UTF_8), List.copyOf(handler.events),
                    factories.get(), handler.closed);
        }
    }

    @Test
    void resourceRunKeepsPublishedOutputAndEvent() {
        RunResult result = capture(new String[0]);
        assertAll(
                () -> assertEquals(0, result.code()),
                () -> assertEquals(expected(3, 240), result.output()),
                () -> assertEquals("", result.error()),
                () -> assertEquals(1, result.factories()),
                () -> assertEquals(1, result.events().size()),
                () -> assertEquals(Level.INFO, result.events().getFirst().getLevel()),
                () -> assertEquals("EXCHANGE_OK count=3", result.events().getFirst().getMessage()),
                () -> assertTrue(result.closed()));
    }

    @Test
    void emptyJsonIsSuccessfulInput() throws Exception {
        Path input = directory.resolve("empty.json");
        Files.writeString(input, "[]", StandardCharsets.UTF_8);
        RunResult result = capture(new String[]{"--inspect", "json", input.toString()});
        assertAll(
                () -> assertEquals(0, result.code()),
                () -> assertEquals(expected(0, 0), result.output()),
                () -> assertEquals("", result.error()),
                () -> assertEquals("EXCHANGE_OK count=0", result.events().getFirst().getMessage()));
    }

    @Test
    void invalidFormatDoesNotCreateLogger() {
        RunResult result = capture(new String[]{"--inspect", "yaml", "missing.yaml"});
        assertAll(
                () -> assertEquals(2, result.code()),
                () -> assertEquals("", result.output()),
                () -> assertEquals("Помилка: некоректні аргументи." + System.lineSeparator(),
                        result.error()),
                () -> assertEquals(0, result.factories()),
                () -> assertTrue(result.events().isEmpty()),
                () -> assertFalse(result.closed()));
    }

    @Test
    void missingInputHasIoCodeAndEvent() {
        Path missing = directory.resolve("missing.json");
        RunResult result = capture(new String[]{"--inspect", "json", missing.toString()});
        assertAll(
                () -> assertEquals(4, result.code()),
                () -> assertEquals("", result.output()),
                () -> assertEquals("Помилка читання або запису." + System.lineSeparator(),
                        result.error()),
                () -> assertEquals(Level.SEVERE, result.events().getFirst().getLevel()),
                () -> assertEquals("EXCHANGE_IO_ERROR", result.events().getFirst().getMessage()),
                () -> assertInstanceOf(IOException.class, result.events().getFirst().getThrown()),
                () -> assertTrue(result.closed()));
    }

    @Test
    void malformedJsonHasDataCodeAndEvent() throws Exception {
        Path input = directory.resolve("malformed.json");
        Files.writeString(input, "[{", StandardCharsets.UTF_8);
        RunResult result = capture(new String[]{"--inspect", "json", input.toString()});
        assertAll(
                () -> assertEquals(3, result.code()),
                () -> assertEquals("", result.output()),
                () -> assertEquals("Помилка даних CSV або JSON." + System.lineSeparator(),
                        result.error()),
                () -> assertEquals(Level.WARNING, result.events().getFirst().getLevel()),
                () -> assertEquals("EXCHANGE_DATA_ERROR", result.events().getFirst().getMessage()),
                () -> assertInstanceOf(ExchangeFormatException.class,
                        result.events().getFirst().getThrown()));
    }

    @Test
    void seedDoesNotOverwriteExistingBytes() throws Exception {
        Path target = directory.resolve("existing.json");
        byte[] original = "КОНТРОЛЬ".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);
        RunResult result = capture(new String[]{"--seed", target.toString()});
        assertEquals(4, result.code());
        assertEquals("", result.output());
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void seedCreatesReadableJsonAtUnicodePath() throws Exception {
        Path target = directory.resolve("дані з пробілом/requests.json");
        RunResult result = capture(new String[]{"--seed", target.toString()});
        RequestFiles files = new RequestFiles();
        assertAll(
                () -> assertEquals(0, result.code()),
                () -> assertEquals(expected(3, 240), result.output()),
                () -> assertEquals(files.readResource(), files.read(target, "json")));
    }

    @Test
    void runDoesNotCloseCallerStreams() {
        TrackingBuffer output = new TrackingBuffer();
        TrackingBuffer error = new TrackingBuffer();
        ProbeHandler handler = new ProbeHandler();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            assertEquals(0, FileExchangeApp.run(new String[0], out, err, () -> logger(handler)));
            assertFalse(output.closed);
            assertFalse(error.closed);
            out.println("Після запуску");
            err.println("Діагностика після запуску");
            assertTrue(output.toString(StandardCharsets.UTF_8)
                    .endsWith("Після запуску" + System.lineSeparator()));
            assertEquals("Діагностика після запуску" + System.lineSeparator(),
                    error.toString(StandardCharsets.UTF_8));
        }
    }
    private static Stream<Arguments> invalidCliArguments() {
        return Stream.of(
                Arguments.of((Object) new String[]{"--unknown"}),
                Arguments.of((Object) new String[]{"--inspect", "JSON", "missing.json"}),
                Arguments.of((Object) new String[]{"--convert", "csv"}),
                Arguments.of((Object) new String[]{"--seed", ""}),
                Arguments.of((Object) new String[]{"--seed", "   "}),
                Arguments.of((Object) new String[]{"--resource", "extra"}));
    }

    @ParameterizedTest
    @MethodSource("invalidCliArguments")
    void rejectsInvalidCliArgumentsBeforeCreatingLogger(String[] args) {
        RunResult result = capture(args);
        assertAll(
                () -> assertEquals(2, result.code()),
                () -> assertEquals("", result.output()),
                () -> assertEquals("Помилка: некоректні аргументи." + System.lineSeparator(),
                        result.error()),
                () -> assertEquals(0, result.factories()),
                () -> assertTrue(result.events().isEmpty()),
                () -> assertFalse(result.closed()));
    }
}