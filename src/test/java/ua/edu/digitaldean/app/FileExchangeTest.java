package ua.edu.digitaldean.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.service.CsvCodec;
import ua.edu.digitaldean.service.ExchangeFormatException;
import ua.edu.digitaldean.service.JsonCodec;
import ua.edu.digitaldean.service.RequestFileRow;
import ua.edu.digitaldean.service.RequestFiles;

public final class FileExchangeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  ВИКОНАННЯ API-СЦЕНАРІЇВ РОЗДІЛУ 9.2 (ПРАКТИЧНА №10) ");
        System.out.println("==================================================");

        Path tempRoot = Files.createTempDirectory("digital-dean-test10-");
        try {
            runTestResolveInside(tempRoot);
            runTestDelimiterDialect(tempRoot);
            runTestBomHandling(tempRoot);
            runTestByteLimits(tempRoot);
            runTestCreateNewAndInvalidUtf8(tempRoot);
            runTestSupplementaryCodePointsAndLineEndings(tempRoot);
            runTestJsonFieldOrderAndEscapes();
            runTestProbeAndNonFileTarget(tempRoot);
            runTestNullAndBorrowedResources();
        } finally {
            deleteRecursively(tempRoot);
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("Усі API-тести практичної роботи №10 успішно пройдено!");
    }

    private static void printHeader(String id, String desc, String call) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [Тест " + id + "] " + desc);
        System.out.println("API-виклик: " + call);
        System.out.println("Результат перевірки:");
    }

    private static void printPassed() {
        System.out.println("Статус: [ВІДПОВІДАЄ]");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Помилка перевірки: " + message);
        }
    }

    private static void runTestResolveInside(Path root) {
        printHeader("1-3 (API)", "resolveInside: нормалізація всередині бази та відхилення виходу ../",
                "RequestFiles.resolveInside(base, path)");

        Path p1 = RequestFiles.resolveInside(root, "sub/../data/ результати.json");
        check(p1.startsWith(root.toAbsolutePath().normalize()), "Нормалізований шлях у межах бази");

        try { RequestFiles.resolveInside(root, "../outside.csv"); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { RequestFiles.resolveInside(root, "/abs.csv"); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { RequestFiles.resolveInside(root, "   "); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { RequestFiles.resolveInside(null, "a.csv"); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { RequestFiles.resolveInside(root, null); throw new AssertionError(); } catch (NullPointerException expected) {}

        System.out.println("  * sub/../data/ результати.json -> дозволено в межах бази");
        System.out.println("  * ../outside.csv, абсолютний шлях та null -> відхилено (IAE / NPE)");
        printPassed();
    }

    private static void runTestDelimiterDialect(Path root) throws IOException {
        printHeader("4-5 (API)", "Діалект CSV із ';' та перевірка конфігурації CsvCodec",
                "new CsvCodec(';'); round trip CSV(;) -> JSON -> CSV(;)");

        try { new CsvCodec(':'); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        check(new CsvCodec().delimiter() == ',', "Типовий конструктор зберігає ','");

        RequestFileRow row = new RequestFileRow(
                "REQ-003", new StudentId("ST-2025-0042"), "uk", 2, "Кома, крапка; лапка \"");
        Path csv1 = root.resolve("semi1.csv");
        Path json = root.resolve("semi.json");
        Path csv2 = root.resolve("semi2.csv");

        RequestFiles semicolonFiles = new RequestFiles(RequestFiles.DEFAULT_MAX_BYTES, ';');
        semicolonFiles.write(csv1, "csv", List.of(row));
        List<RequestFileRow> fromCsv1 = semicolonFiles.read(csv1, "csv");
        semicolonFiles.write(json, "json", fromCsv1);
        List<RequestFileRow> fromJson = semicolonFiles.read(json, "json");
        semicolonFiles.write(csv2, "csv", fromJson);
        List<RequestFileRow> finalRows = semicolonFiles.read(csv2, "csv");

        check(finalRows.equals(List.of(row)), "Кругове перетворення з ';' зберегло всі компоненти");
        String rawCsv = Files.readString(csv2, StandardCharsets.UTF_8);
        check(rawCsv.startsWith("requestId;studentId;language;copies;note\r\n"), "Заголовок із ';' і CRLF");

        System.out.println("  * Заголовок і лапки для ';' збережено, round trip еквівалентний");
        printPassed();
    }

    private static void runTestBomHandling(Path root) throws IOException {
        printHeader("6-8 (API)", "Приймання одного початкового BOM, відмова на двох BOM, збереження внутрішнього U+FEFF",
                "files.read(bomFile); files.write(outWithoutBom)");

        RequestFiles files = new RequestFiles();
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

        // 1. Один BOM перед порожнім JSON []
        Path oneBomJson = root.resolve("one_bom.json");
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        bos1.write(bom);
        bos1.write("[]\n".getBytes(StandardCharsets.UTF_8));
        Files.write(oneBomJson, bos1.toByteArray());
        check(files.read(oneBomJson, "json").isEmpty(), "Один BOM перед [] успішно прочитано");

        // 2. Два початкові BOM -> ExchangeFormatException
        Path twoBomJson = root.resolve("two_bom.json");
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        bos2.write(bom);
        bos2.write(bom);
        bos2.write("[]\n".getBytes(StandardCharsets.UTF_8));
        Files.write(twoBomJson, bos2.toByteArray());
        try {
            files.read(twoBomJson, "json");
            throw new AssertionError("Два BOM мали спричинити ExchangeFormatException");
        } catch (ExchangeFormatException expected) {}

        // 3. U+FEFF усередині примітки зберігається як дані, а вихідний файл записується без BOM
        RequestFileRow rowWithInternalFeff = new RequestFileRow(
                "REQ-001", new StudentId("ST-2025-0007"), "uk", 1, "Текст\uFEFFвсередині");
        Path outJson = root.resolve("no_bom_out.json");
        files.write(outJson, "json", List.of(rowWithInternalFeff));
        byte[] writtenBytes = Files.readAllBytes(outJson);
        check(!(writtenBytes[0] == (byte) 0xEF && writtenBytes[1] == (byte) 0xBB && writtenBytes[2] == (byte) 0xBF),
                "Вихідний файл не містить початкового BOM");
        List<RequestFileRow> readBack = files.read(outJson, "json");
        check(readBack.get(0).note().equals("Текст\uFEFFвсередині"), "Внутрішній U+FEFF збережено");

        System.out.println("  * 1 BOM знято, 2 BOM відхилено, внутрішній U+FEFF збережено, вихід без BOM");
        printPassed();
    }

    private static void runTestByteLimits(Path root) throws IOException {
        printHeader("11-12, 23 (API)", "Обмеження байтів входу (N=S, N=S-1, N=0) та порівняння байтів проти String.length()",
                "new RequestFiles(maxBytes).read(...)");

        try { new RequestFiles(-1); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { new RequestFiles(1_048_577); throw new AssertionError(); } catch (IllegalArgumentException expected) {}

        Path sample = root.resolve("ukr_limit.json");
        String content = "[\n  {\"requestId\":\"REQ-001\",\"studentId\":\"ST-2025-0007\",\"language\":\"uk\",\"copies\":1,\"note\":\"Привіт\"}\n]\n";
        byte[] utf8Bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(sample, utf8Bytes);

        int exactByteCount = utf8Bytes.length;
        int charCount = content.length();
        check(exactByteCount > charCount, "Українські літери займають більше байтів UTF-8, ніж char");

        // Точно S байтів -> успіх
        check(new RequestFiles(exactByteCount).read(sample, "json").size() == 1, "За N=S читається успішно");

        // S-1 байтів -> ExchangeFormatException
        try {
            new RequestFiles(exactByteCount - 1).read(sample, "json");
            throw new AssertionError("S-1 має перевищувати ліміт");
        } catch (ExchangeFormatException expected) {}

        // Ліміт за довжиною String.length() недостатній для UTF-8 байтів
        try {
            new RequestFiles(charCount).read(sample, "json");
            throw new AssertionError("Ліміт рахується в байтах, не в char");
        } catch (ExchangeFormatException expected) {}

        // N=0 -> порожній текст не проходить схему
        Path zeroFile = root.resolve("zero.json");
        Files.write(zeroFile, new byte[0]);
        try {
            new RequestFiles(0).read(zeroFile, "json");
            throw new AssertionError("Порожній файл за N=0 не відповідає схемі");
        } catch (ExchangeFormatException expected) {}

        System.out.println("  * Точний розмір S=" + exactByteCount + " байтів (String.length=" + charCount + "): S -> OK, S-1 -> відмова");
        printPassed();
    }

    private static void runTestCreateNewAndInvalidUtf8(Path root) throws IOException {
        printHeader("13-14 (API)", "Декодування некоректного UTF-8 з REPORT та захист наявного файла CREATE_NEW",
                "files.read(badUtf8); files.write(existingFile)");

        RequestFiles files = new RequestFiles();
        Path badUtf8 = root.resolve("bad_utf8.csv");
        Files.write(badUtf8, new byte[]{(byte) 0xFF, (byte) 0xFE, 0x41});

        boolean malformedCaught = false;
        try {
            files.read(badUtf8, "csv");
        } catch (ExchangeFormatException e) {
            throw new AssertionError("Має бути IOException декодування, а не ExchangeFormatException");
        } catch (IOException e) {
            malformedCaught = true;
        }
        check(malformedCaught, "Некоректний UTF-8 відхилено декодером REPORT");

        Path existing = root.resolve("existing.json");
        byte[] controlBytes = "КОНТРОЛЬНІ БАЙТИ".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, controlBytes);

        try {
            files.write(existing, "json", List.of());
            throw new AssertionError("Перезапис наявного файла має бути заборонений");
        } catch (IOException expected) {}

        check(Arrays.equals(Files.readAllBytes(existing), controlBytes), "Контрольні байти наявного файла не змінилися");
        System.out.println("  * Некоректний UTF-8 -> IOException; наявний файл захищено через CREATE_NEW");
        printPassed();
    }

    private static void runTestSupplementaryCodePointsAndLineEndings(Path root) throws IOException {
        printHeader("15-16 (API)", "200 кодових точок поза BMP (суррогатні пари) та збереження LF/CRLF у примітках",
                "new RequestFileRow(..., 200 emoji); round trip CSV <-> JSON");

        String emoji = "\uD83D\uDE80"; // 1 кодова точка, але 2 char (length == 2)
        String note200 = emoji.repeat(200);
        check(note200.length() == 400 && note200.codePointCount(0, note200.length()) == 200, "400 char = 200 code points");

        RequestFileRow row1 = new RequestFileRow("REQ-001", new StudentId("ST-2025-0007"), "uk", 1, note200);
        RequestFileRow row2 = new RequestFileRow("REQ-002", new StudentId("ST-2025-0042"), "en", 2, "Рядок1\nРядок2\r\nРядок3");

        String note201 = emoji.repeat(201);
        try {
            new RequestFileRow("REQ-003", new StudentId("ST-2025-0007"), "uk", 1, note201);
            throw new AssertionError("201 кодова точка має відхилятися");
        } catch (IllegalArgumentException expected) {}

        RequestFiles files = new RequestFiles();
        Path csvPath = root.resolve("bmp.csv");
        Path jsonPath = root.resolve("bmp.json");
        files.write(csvPath, "csv", List.of(row1, row2));
        List<RequestFileRow> r1 = files.read(csvPath, "csv");
        files.write(jsonPath, "json", r1);
        List<RequestFileRow> r2 = files.read(jsonPath, "json");

        check(r2.equals(List.of(row1, row2)), "200 кодових точок поза BMP та внутрішні LF/CRLF збережені");
        System.out.println("  * 200 кодових точок поза BMP (400 char) прийнято, 201 — відхилено; LF і CRLF збережено");
        printPassed();
    }

    private static void runTestJsonFieldOrderAndEscapes() throws IOException {
        printHeader("17 (API)", "JSON із переставленими полями та Unicode-екрануванням",
                "JsonCodec.read(reorderedAndEscapedJson)");

        String json = "[{\"note\":\"\\u0414\\u043e\\u0432\\u0456\\u0434\\u043a\\u0430\",\"copies\":2,\"language\":\"uk\",\"studentId\":\"ST-2025-0042\",\"requestId\":\"REQ-003\"}]";
        JsonCodec codec = new JsonCodec();
        List<RequestFileRow> rows = codec.read(new StringReader(json));

        RequestFileRow expected = new RequestFileRow("REQ-003", new StudentId("ST-2025-0042"), "uk", 2, "Довідка");
        check(rows.equals(List.of(expected)), "Переставлені поля та \\uXXXX декодовано в еталонний об'єкт");

        StringWriter out = new StringWriter();
        codec.write(out, rows);
        check(out.toString().contains("{\"requestId\":\"REQ-003\",\"studentId\":\"ST-2025-0042\",\"language\":\"uk\",\"copies\":2,\"note\":\"Довідка\"}"),
                "Вихід має канонічний порядок полів");

        System.out.println("  * Семантика JSON не залежить від порядку полів; запис формує канонічний порядок");
        printPassed();
    }

    private static void runTestProbeAndNonFileTarget(Path root) throws IOException {
        printHeader("19-20 (API)", "Дослід файлової системи probe(dir) та захист звичайного файла",
                "files.probe(checksDir); files.probe(regularFile)");

        RequestFiles files = new RequestFiles();
        Path checksDir = root.resolve("checks");
        RequestFiles.ProbeReport report = files.probe(checksDir);

        try (var stream = Files.list(checksDir)) {
            check(stream.findAny().isEmpty(), "Тимчасовий каталог прибрано після probe");
        }

        Path regularFile = root.resolve("not_a_dir.txt");
        byte[] content = "ДАНІ ФАЙЛА".getBytes(StandardCharsets.UTF_8);
        Files.write(regularFile, content);

        try {
            files.probe(regularFile);
            throw new AssertionError("probe на звичайному файлі має кидати IOException");
        } catch (IOException expected) {}

        check(Arrays.equals(Files.readAllBytes(regularFile), content), "Звичайний файл не пошкоджено і не видалено");
        System.out.println("  * Результат досліду сховища: POSIX=" + report.posix() + ", DOS=" + report.dos() + ", Регістри=" + report.caseSensitivity());
        System.out.println("  * Тимчасовий каталог прибрано, чужий файл збережено");
        printPassed();
    }

    private static void runTestNullAndBorrowedResources() throws IOException {
        printHeader("21-22 (API)", "Незмінність результатів та незакриття позичених Reader/Writer у кодеках",
                "CsvCodec/JsonCodec з позиченими потоками");

        final boolean[] readerClosed = {false};
        Reader borrowedReader = new StringReader("[]") {
            @Override
            public void close() {
                readerClosed[0] = true;
                super.close();
            }
        };
        List<RequestFileRow> list = new JsonCodec().read(borrowedReader);
        check(!readerClosed[0], "JsonCodec не закриває позичений Reader");

        try {
            list.add(new RequestFileRow("REQ-001", new StudentId("ST-2025-0007"), "uk", 1, ""));
            throw new AssertionError("Список має бути unmodifiable");
        } catch (UnsupportedOperationException expected) {}

        final boolean[] writerClosed = {false};
        Writer borrowedWriter = new StringWriter() {
            @Override
            public void close() throws IOException {
                writerClosed[0] = true;
                super.close();
            }
        };
        new CsvCodec().write(borrowedWriter, List.of());
        check(!writerClosed[0], "CsvCodec не закриває позичений Writer");

        System.out.println("  * Позичені Reader/Writer залишаються відкритими; списки захищені від модифікації");
        printPassed();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            List<Path> paths = new ArrayList<>(walk.toList());
            paths.sort((a, b) -> b.compareTo(a));
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}