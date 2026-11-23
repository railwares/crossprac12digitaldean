package ua.edu.digitaldean.service;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.edu.digitaldean.model.RequestKey;

public final class RequestFiles {
    public static final int DEFAULT_MAX_BYTES = 1_048_576;
    private static final String RESOURCE_NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*\\.csv";

    private final int maxBytes;
    private final char csvDelimiter;

    public record ProbeReport(boolean posix, boolean dos, String caseSensitivity) {
    }

    public RequestFiles() {
        this(DEFAULT_MAX_BYTES, ',');
    }

    // Зміна 5: конфігурація обмеження байтів входу
    public RequestFiles(int maxBytes) {
        this(maxBytes, ',');
    }

    public RequestFiles(int maxBytes, char csvDelimiter) {
        if (maxBytes < 0 || maxBytes > DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "Ліміт байтів має бути в межах 0.." + DEFAULT_MAX_BYTES + ".");
        }
        if (csvDelimiter != ',' && csvDelimiter != ';') {
            throw new IllegalArgumentException("Розділювач CSV має бути ',' або ';'.");
        }
        this.maxBytes = maxBytes;
        this.csvDelimiter = csvDelimiter;
    }

    // Зміна 1: обчислення та лексична перевірка відносного шляху всередині базового каталогу
    public static Path resolveInside(Path base, String value) {
        Objects.requireNonNull(base, "Базовий каталог не може бути null.");
        Objects.requireNonNull(value, "Шлях не може бути null.");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Шлях не може бути порожнім.");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path relative = Path.of(value);
        if (relative.isAbsolute() || relative.getRoot() != null) {
            throw new IllegalArgumentException("Шлях має бути відносним без кореня.");
        }
        Path resolved = normalizedBase.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Шлях виходить за межі базового каталогу.");
        }
        return resolved;
    }

    public List<RequestFileRow> readResource() throws IOException {
        return readResource("requests.csv");
    }

    // Зміна 4: вибір CSV-ресурсу з JAR за простим ім'ям
    public List<RequestFileRow> readResource(String name) throws IOException {
        Objects.requireNonNull(name, "Ім'я ресурсу не може бути null.");
        if (!name.matches(RESOURCE_NAME_PATTERN)) {
            throw new IllegalArgumentException("Некоректне ім'я ресурсу: " + name);
        }
        InputStream input = RequestFiles.class.getResourceAsStream("/digital-dean/" + name);
        if (input == null) {
            throw new IOException("Ресурс застосунку відсутній: " + name);
        }
        try (InputStream stream = input) {
            String content = readBoundedAndStripBom(stream);
            try (Reader reader = new StringReader(content)) {
                return checked(new CsvCodec(csvDelimiter).read(reader));
            }
        }
    }

    public List<RequestFileRow> read(Path path, String format) throws IOException {
        Objects.requireNonNull(path, "Шлях не може бути null.");
        requireFormat(format);
        try (InputStream stream = Files.newInputStream(path)) {
            String content = readBoundedAndStripBom(stream);
            try (Reader reader = new StringReader(content)) {
                return checked(readFromReader(reader, format));
            }
        }
    }

    public void write(Path path, String format, List<RequestFileRow> rows) throws IOException {
        Objects.requireNonNull(path, "Шлях не може бути null.");
        requireFormat(format);
        List<RequestFileRow> snapshot = checked(rows);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if (format.equals("csv")) {
                new CsvCodec(csvDelimiter).write(writer, snapshot);
            } else {
                new JsonCodec().write(writer, snapshot);
            }
        }
    }

    // Зміна 6: експериментальне дослідження файлової системи
    public ProbeReport probe(Path directory) throws IOException {
        Objects.requireNonNull(directory, "Каталог для досліду не може бути null.");
        Files.createDirectories(directory);
        Path tempDir = Files.createTempDirectory(directory, "probe-");
        Path lower = tempDir.resolve("report.txt");
        Path upper = tempDir.resolve("REPORT.txt");
        boolean upperCreated = false;
        try {
            Files.newBufferedWriter(lower, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close();
            String sensitivity;
            try {
                Files.newBufferedWriter(upper, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close();
                upperCreated = true;
                sensitivity = "різні";
            } catch (FileAlreadyExistsException exception) {
                if (Files.isSameFile(lower, upper)) {
                    sensitivity = "спільний";
                } else {
                    throw exception;
                }
            }
            FileStore store = Files.getFileStore(tempDir);
            boolean posix = store.supportsFileAttributeView("posix");
            boolean dos = store.supportsFileAttributeView("dos");
            return new ProbeReport(posix, dos, sensitivity);
        } finally {
            try {
                if (upperCreated) {
                    Files.deleteIfExists(upper);
                }
            } finally {
                try {
                    Files.deleteIfExists(lower);
                } finally {
                    Files.deleteIfExists(tempDir);
                }
            }
        }
    }

    public static void requireFormat(String format) {
        Objects.requireNonNull(format, "Формат не може бути null.");
        if (!format.equals("csv") && !format.equals("json")) {
            throw new IllegalArgumentException("Формат має бути csv або json.");
        }
    }

    private List<RequestFileRow> readFromReader(Reader reader, String format) throws IOException {
        return format.equals("csv")
                ? new CsvCodec(csvDelimiter).read(reader)
                : new JsonCodec().read(reader);
    }

    // Зміни 3 і 5: обмеження байтів (readNBytes(maxBytes + 1)), декодування з REPORT та зняття одного BOM
    private String readBoundedAndStripBom(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new ExchangeFormatException("Перевищено ліміт байтів входу.");
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        StringWriter writer = new StringWriter();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), decoder)) {
            reader.transferTo(writer);
        }
        String text = writer.toString();
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private static List<RequestFileRow> checked(List<RequestFileRow> rows)
            throws ExchangeFormatException {
        List<RequestFileRow> snapshot = List.copyOf(
                Objects.requireNonNull(rows, "Список рядків не може бути null."));
        Set<RequestKey> keys = new HashSet<>();
        for (RequestFileRow row : snapshot) {
            if (!keys.add(new RequestKey(row.requestId()))) {
                throw new ExchangeFormatException("Повторний ключ заяви.");
            }
        }
        return snapshot;
    }
}