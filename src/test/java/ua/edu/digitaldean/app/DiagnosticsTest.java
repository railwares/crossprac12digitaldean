package ua.edu.digitaldean.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.EnrollmentRequest;
import ua.edu.digitaldean.model.RequestKey;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.DiagnosticLog;
import ua.edu.digitaldean.service.DuplicateRequestException;
import ua.edu.digitaldean.service.ImportReport;
import ua.edu.digitaldean.service.RequestAnalytics;
import ua.edu.digitaldean.service.RequestCatalog;
import ua.edu.digitaldean.service.RequestDataException;
import ua.edu.digitaldean.service.RequestImporter;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.RequestRow;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class DiagnosticsTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ВИКОНАННЯ API-СЦЕНАРІЇВ РОЗДІЛУ 9.2 (ПРАКТИЧНА №9) ");
        System.out.println("==================================================");

        runTest7();
        runTest8();
        runTest9();
        runTest10();
        runTest11();
        runTest12();
        runTest13();
        runTest14();
        runTest22();
        runTest24();

        System.out.println("\n--------------------------------------------------");
        System.out.println("Усі API-тести практичної роботи №9 успішно пройдено!");
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

    private static void runTest7() {
        printHeader("7", "registerUnique для двох заяв з однаковим нормалізованим ключем",
                "importer.registerUnique([REQ-003, req-003], emptyCatalog)");

        RequestImporter importer = new RequestImporter();
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        StudentId s1 = new StudentId("ST-2025-0042");
        StudentId s2 = new StudentId("ST-2025-0007");

        List<TranscriptRequest> list = List.of(
                new TranscriptRequest("REQ-003", s1, "uk", 2),
                new TranscriptRequest(" req-003 ", s2, "en", 1)
        );

        DuplicateRequestException caught = null;
        try {
            importer.registerUnique(list, catalog);
        } catch (DuplicateRequestException e) {
            caught = e;
        }

        check(caught != null, "Має викидатися DuplicateRequestException");
        check(caught.key().equals(new RequestKey("REQ-003")), "Ключ винятку REQ-003");
        check(catalog.size() == 0 && catalog.pendingCount() == 0, "Реєстр і черга порожні");

        System.out.println("  * Перехоплено: DuplicateRequestException, key=" + caught.key().value());
        System.out.println("  * Стан після відмови: size=" + catalog.size() + ", pending=" + catalog.pendingCount());
        printPassed();
    }

    private static void runTest8() {
        printHeader("8", "registerUnique коли дублікат уже є в наявному реєстрі",
                "catalog(REQ-003) <- registerUnique([REQ-010, REQ-003])");

        RequestImporter importer = new RequestImporter();
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        StudentId s = new StudentId("ST-2025-0042");
        catalog.add(new TranscriptRequest("REQ-003", s, "uk", 2));

        List<TranscriptRequest> batch = List.of(
                new TranscriptRequest("REQ-010", s, "en", 1),
                new TranscriptRequest("REQ-003", s, "uk", 3)
        );

        DuplicateRequestException caught = null;
        try {
            importer.registerUnique(batch, catalog);
        } catch (DuplicateRequestException e) {
            caught = e;
        }

        check(caught != null && caught.key().equals(new RequestKey("REQ-003")), "Виявлено REQ-003");
        check(catalog.size() == 1 && catalog.pendingCount() == 1, "REQ-010 не додано");
        check(catalog.find(new RequestKey("REQ-010")).isEmpty(), "REQ-010 відсутня");

        System.out.println("  * Виявлено конфлікт ключа: " + caught.key().value());
        System.out.println("  * REQ-010 не потрапила до реєстру (атомарність збережено, size=1)");
        printPassed();
    }

    private static void runTest9() {
        printHeader("9", "Порядок вибору першого повторення у списку [REQ-002, REQ-002, REQ-001, REQ-001]",
                "importer.registerUnique([REQ-002, REQ-002, REQ-001, REQ-001], catalog)");

        RequestImporter importer = new RequestImporter();
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        StudentId s = new StudentId("ST-2025-0042");

        List<TranscriptRequest> list = List.of(
                new TranscriptRequest("REQ-002", s, "uk", 1),
                new TranscriptRequest("REQ-002", s, "en", 2),
                new TranscriptRequest("REQ-001", s, "uk", 1),
                new TranscriptRequest("REQ-001", s, "en", 2)
        );

        DuplicateRequestException caught = null;
        try {
            importer.registerUnique(list, catalog);
        } catch (DuplicateRequestException e) {
            caught = e;
        }

        check(caught != null && caught.key().equals(new RequestKey("REQ-002")), "Перший дублікат REQ-002");
        check(catalog.size() == 0, "Реєстр не змінено");

        System.out.println("  * Перший виявлений дублікат за порядком списку: " + caught.key().value());
        printPassed();
    }

    private static void runTest10() {
        printHeader("10", "Порожній список та перевірка null-посилань у registerUnique",
                "registerUnique(List.of(), cat); registerUnique(null/null-elem)");

        RequestImporter importer = new RequestImporter();
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        StudentId s = new StudentId("ST-2025-0042");

        int count = importer.registerUnique(List.of(), catalog);
        check(count == 0 && catalog.size() == 0, "Порожній список повертає 0");

        try { importer.registerUnique(null, catalog); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { importer.registerUnique(List.of(), null); throw new AssertionError(); } catch (NullPointerException expected) {}
        try {
            importer.registerUnique(Arrays.asList(new TranscriptRequest("REQ-001", s, "uk", 1), null), catalog);
            throw new AssertionError();
        } catch (NullPointerException expected) {}

        check(catalog.size() == 0 && catalog.pendingCount() == 0, "Часткового додавання до NPE не відбулося");

        System.out.println("  * Порожній список -> 0; усі null-випадки кинули NPE до модифікації реєстру");
        printPassed();
    }

    private static void runTest11() {
        printHeader("11", "readAll для набору mixed.txt та перевірка незакриття позиченого ресурсу",
                "importer.readAll(borrowedReader)");

        String content = "REQ-003;ST-2025-0042;uk;2\nREQ-002;ST-2025-0007;en;two\nREQ-006;ST-2025-0099;uk;1\n";
        final boolean[] closed = {false};
        BufferedReader reader = new BufferedReader(new StringReader(content)) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };

        RequestImporter importer = new RequestImporter();
        ImportReport report;
        try {
            report = importer.readAll(reader);
            check(!closed[0], "Позичений ресурс не повинен закриватися в readAll");
            check(reader.readLine() == null, "Читач дійшов до кінця й залишився відкритим");
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        List<String> accIds = report.accepted().stream().map(TranscriptRequest::requestId).toList();
        check(accIds.equals(List.of("REQ-003", "REQ-006")), "Прийнято REQ-003 та REQ-006");
        check(report.rejected().size() == 1, "Одне відхилення");
        RequestDataException ex = report.rejected().get(0);
        check(ex.lineNumber() == 2, "Фізичний рядок 2");
        check(ex.getCause() instanceof NumberFormatException, "Причина NumberFormatException");

        System.out.println("  * accepted: " + accIds);
        System.out.println("  * rejected: line=" + ex.lineNumber() + ", cause=" + ex.getCause().getClass().getSimpleName());
        System.out.println("  * Позичений BufferedReader залишився відкритим: " + !closed[0]);
        printPassed();
    }

    private static void runTest12() {
        printHeader("12", "readAll на двох фізичних рядках: порожній перший і валідний другий",
                "importer.readAll(\"\\nREQ-003;ST-2025-0042;uk;2\")");

        BufferedReader reader = new BufferedReader(new StringReader("\nREQ-003;ST-2025-0042;uk;2\n"));
        RequestImporter importer = new RequestImporter();
        try {
            ImportReport report = importer.readAll(reader);
            check(report.rejected().size() == 1, "1 відхилення");
            check(report.rejected().get(0).lineNumber() == 1, "Номер рядка 1");
            check(report.rejected().get(0).getCause().getClass() == IllegalArgumentException.class, "Причина IllegalArgumentException");
            check(report.accepted().size() == 1 && report.accepted().get(0).requestId().equals("REQ-003"), "Прийнято REQ-003");
            System.out.println("  * Рядок 1 відхилено (IllegalArgumentException), рядок 2 прийнято (REQ-003)");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        printPassed();
    }

    private static void runTest13() {
        printHeader("13", "Відмова читання IOException + пригнічений виняток закриття у власника",
                "try (failingReader) { importer.readAll(failingReader); }");

        IOException readFailure = new IOException("Збій читання рядка 2");
        IOException closeFailure = new IOException("Збій закриття ресурсу");

        class FailingBufferedReader extends BufferedReader {
            private int count = 0;
            FailingBufferedReader() {
                super(new StringReader(""));
            }
            @Override
            public String readLine() throws IOException {
                count++;
                if (count == 1) {
                    return "REQ-003;ST-2025-0042;uk;2";
                }
                throw readFailure;
            }
            @Override
            public void close() throws IOException {
                throw closeFailure;
            }
        }

        RequestImporter importer = new RequestImporter();
        IOException caught = null;
        try (BufferedReader r = new FailingBufferedReader()) {
            importer.readAll(r);
        } catch (IOException e) {
            caught = e;
        }

        check(caught == readFailure, "Основний виняток тотожний винятку читання");
        check(caught.getSuppressed().length == 1 && caught.getSuppressed()[0] == closeFailure,
                "Пригнічений виняток тотожний винятку закриття");

        System.out.println("  * Основний виняток: " + caught.getMessage());
        System.out.println("  * Пригнічений виняток: " + caught.getSuppressed()[0].getMessage());
        printPassed();
    }

    private static void runTest14() {
        printHeader("14", "Незмінність ImportReport та захист від null",
                "new ImportReport(mutableAccepted, mutableRejected)");

        StudentId s = new StudentId("ST-2025-0042");
        TranscriptRequest req = new TranscriptRequest("REQ-003", s, "uk", 2);
        RequestDataException ex = new RequestDataException(1, new IllegalArgumentException("err"));

        List<TranscriptRequest> acc = new ArrayList<>(List.of(req));
        List<RequestDataException> rej = new ArrayList<>(List.of(ex));

        ImportReport report = new ImportReport(acc, rej);
        acc.clear();
        rej.clear();

        check(report.accepted().size() == 1 && report.rejected().size() == 1, "Знімок не змінився");
        try { report.accepted().add(req); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { report.rejected().add(ex); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { new ImportReport(null, List.of()); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { new ImportReport(Arrays.asList((TranscriptRequest) null), List.of()); throw new AssertionError(); } catch (NullPointerException expected) {}

        System.out.println("  * Списки accepted і rejected захищені через List.copyOf (UnsupportedOperationException / NPE)");
        printPassed();
    }

    private static void runTest22() {
        printHeader("22", "Конфігурація DiagnosticLog.create(Level) та перевірка LogRecord через тестовий Handler",
                "DiagnosticLog.create(Level.FINE)");

        try { DiagnosticLog.create(null); throw new AssertionError(); } catch (NullPointerException expected) {}

        Logger log1 = DiagnosticLog.create(Level.FINE);
        Logger log2 = DiagnosticLog.create(Level.WARNING);

        check(log1.getHandlers().length == 1 && log2.getHandlers().length == 1, "По одному власному обробнику");

        List<LogRecord> records = new ArrayList<>();
        for (Handler h : log1.getHandlers()) {
            h.close();
            log1.removeHandler(h);
        }
        Handler testHandler = new Handler() {
            @Override public void publish(LogRecord record) { if (isLoggable(record)) records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        testHandler.setLevel(Level.FINE);
        log1.addHandler(testHandler);

        RequestDataException sampleEx = new RequestDataException(3, new NumberFormatException("bad int"));
        log1.fine("IMPORT_START");
        log1.log(Level.WARNING, "IMPORT_DATA_ERROR", sampleEx);

        check(records.size() == 2, "Записано 2 події");
        check(records.get(0).getLevel() == Level.FINE && records.get(0).getMessage().equals("IMPORT_START"), "Перша подія FINE");
        check(records.get(1).getThrown() == sampleEx, "getThrown тотожний переданому винятку");

        System.out.println("  * Кількість обробників: 1; записано подій: " + records.size());
        System.out.println("  * Перевірено тотожність об'єкта getThrown() == sampleEx");
        printPassed();
    }

    private static void runTest24() {
        printHeader("24", "Збереження стану реєстру та регресійна сумісність із роботою №8",
                "перевірка черги після відмов + контрольний розрахунок RequestAnalytics");

        RequestImporter importer = new RequestImporter();
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        StudentId first = new StudentId("ST-2025-0042");
        StudentId second = new StudentId("ST-2025-0007");
        StudentId third = new StudentId("ST-2025-0099");

        List<AcademicRequest> demo8 = List.of(
                new TranscriptRequest("REQ-003", first, "uk", 2),
                new EnrollmentRequest("REQ-001", second, "CS205"),
                new TranscriptRequest("REQ-002", second, "en", 1),
                new EnrollmentRequest("REQ-004", first, "MA101"),
                new TranscriptRequest("REQ-005", third, "en", 2),
                new TranscriptRequest("REQ-006", third, "uk", 3));

        importer.registerUnique(demo8, catalog);

        try {
            importer.registerUnique(List.of(new TranscriptRequest("REQ-003", first, "uk", 1)), catalog);
            throw new AssertionError();
        } catch (DuplicateRequestException expected) {}

        RequestAnalytics analytics = new RequestAnalytics(new RequestProcessor(new StandardFeePolicy()));
        List<RequestRow> rows = analytics.project(catalog.snapshot());
        List<RequestRow> selected = analytics.select(rows, new BigDecimal("40.00"), 3);

        List<String> selKeys = selected.stream().map(r -> r.requestId().value()).toList();
        check(selKeys.equals(List.of("REQ-006", "REQ-003", "REQ-005")), "Вибірка №8 збережена");
        check(analytics.totalFee(selected).equals(new BigDecimal("280.00")), "Сума вибірки 280.00");

        List<String> fifo = new ArrayList<>();
        Optional<AcademicRequest> next;
        while ((next = catalog.pollPending()).isPresent()) {
            fifo.add(next.get().requestId());
        }
        check(fifo.equals(List.of("REQ-003", "REQ-001", "REQ-002", "REQ-004", "REQ-005", "REQ-006")),
                "Порядок черги не порушено");

        System.out.println("  * Вибірка №8: " + selKeys + ", сума: 280.00 UAH");
        System.out.println("  * Контрольне вичерпування черги: " + fifo);
        printPassed();
    }
}