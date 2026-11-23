package ua.edu.digitaldean.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.EnrollmentRequest;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.RequestAnalytics;
import ua.edu.digitaldean.service.RequestCatalog;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.RequestRow;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class DigitalDeanApp {
    private static final String AMOUNT_PATTERN = "[0-9]+(?:\\.[0-9]{1,2})?";
    private static final String LIMIT_PATTERN = "0|[1-9][0-9]*";

    private DigitalDeanApp() {
    }

    public static void main(String[] args) {
        try {
            dispatch(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Помилка: некоректні аргументи.");
            System.exit(2);
        }
    }

    private static void dispatch(String[] args) {
        if (args.length == 0) {
            printBaseReport(false, new BigDecimal("40.00"), 3);
            return;
        }

        String first = args[0];
        if (first.equals("--empty")) {
            if (args.length != 1) {
                throw new IllegalArgumentException("Зайві аргументи для --empty.");
            }
            printBaseReport(true, new BigDecimal("40.00"), 3);
            return;
        }

        if (first.equals("--range")) {
            handleRange(args);
            return;
        }

        if (first.equals("--compare")) {
            handleCompare(args);
            return;
        }

        if (first.startsWith("--") || args.length != 2) {
            throw new IllegalArgumentException("Невідомий режим або кількість аргументів.");
        }

        BigDecimal minFee = parseAmount(args[0]);
        int limit = parseLimit(args[1]);
        printBaseReport(false, minFee, limit);
    }

    private static void handleRange(String[] args) {
        if (args.length != 5) {
            throw new IllegalArgumentException("Очікується 5 аргументів для --range.");
        }

        String rawStudent = args[1];
        if (rawStudent == null || rawStudent.isBlank()) {
            throw new IllegalArgumentException("Порожній аргумент студента.");
        }

        Set<StudentId> allowedStudents;
        if (rawStudent.equals("ALL")) {
            allowedStudents = Set.of();
        } else {
            allowedStudents = Set.of(new StudentId(rawStudent));
        }

        BigDecimal minFee = parseAmount(args[2]);
        BigDecimal maxFee = parseAmount(args[3]);
        int limit = parseLimit(args[4]);

        if (maxFee.compareTo(minFee) < 0) {
            throw new IllegalArgumentException("Мінімальний збір перевищує максимальний.");
        }

        RequestCatalog<AcademicRequest> catalog = createDemoCatalog();
        RequestAnalytics analytics = createAnalytics();
        List<RequestRow> rows = analytics.project(catalog.snapshot());
        List<RequestRow> selected = analytics.select(rows, allowedStudents, minFee, maxFee, limit);

        for (RequestRow row : selected) {
            System.out.println(row.formattedLine());
        }
        System.out.println("Відібрано заяв: " + selected.size());
        System.out.println("Сума вибірки: " + analytics.totalFee(selected).toPlainString() + " UAH");
        System.out.println("Середнє вибірки: " + analytics.averageFee(selected)
                .map(val -> val.toPlainString() + " UAH").orElse("НЕМАЄ ДАНИХ"));
        System.out.println("Найбільший збір у вибірці: " + analytics.highestFee(selected)
                .map(row -> row.requestId().value()).orElse("НЕМАЄ ДАНИХ"));
    }

    private static void handleCompare(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Очікується 3 аргументи для --compare.");
        }

        BigDecimal minFee = parseAmount(args[1]);
        int limit = parseLimit(args[2]);

        RequestCatalog<AcademicRequest> catalog = createDemoCatalog();
        RequestAnalytics analytics = createAnalytics();
        List<RequestRow> rows = analytics.project(catalog.snapshot());

        List<RequestRow> sortedBeforeLimit = analytics.select(rows, minFee, limit);
        List<RequestRow> limitBeforeSorted = analytics.selectPrefixThenSort(rows, minFee, limit);

        System.out.println("Сортування перед лімітом: " + formatKeys(sortedBeforeLimit));
        System.out.println("Сума: " + analytics.totalFee(sortedBeforeLimit).toPlainString() + " UAH");
        System.out.println("Ліміт перед сортуванням: " + formatKeys(limitBeforeSorted));
        System.out.println("Сума: " + analytics.totalFee(limitBeforeSorted).toPlainString() + " UAH");
    }

    private static void printBaseReport(boolean empty, BigDecimal minimumFee, int limit) {
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        if (!empty) {
            for (AcademicRequest request : demoRequests()) {
                catalog.add(request);
            }
        }
        int pendingBefore = catalog.pendingCount();
        RequestAnalytics analytics = createAnalytics();
        List<RequestRow> rows = analytics.project(catalog.snapshot());
        List<RequestRow> selected = analytics.select(rows, minimumFee, limit);

        System.out.println("Заяв у реєстрі: " + catalog.size());
        System.out.println("Мінімальний збір: "
                + minimumFee.setScale(2).toPlainString() + " UAH");
        System.out.println("Ліміт: " + limit);
        System.out.println("Вибрані заяви:");
        for (RequestRow row : selected) {
            System.out.println(row.formattedLine());
        }
        System.out.println("Відібрано заяв: " + selected.size());
        System.out.println("Сума вибірки: " + analytics.totalFee(selected).toPlainString() + " UAH");
        System.out.println("Середнє вибірки: " + analytics.averageFee(selected)
                .map(value -> value.toPlainString() + " UAH").orElse("НЕМАЄ ДАНИХ"));
        System.out.println("Найбільший збір у вибірці: " + analytics.highestFee(selected)
                .map(row -> row.requestId().value()).orElse("НЕМАЄ ДАНИХ"));

        String students = analytics.students(selected).stream()
                .map(StudentId::value).collect(Collectors.joining(", "));
        System.out.println("Студенти вибірки: " + (students.isEmpty() ? "НЕМАЄ" : students));

        String counts = analytics.countByType(selected).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        System.out.println("Кількість за типами у вибірці: " + (counts.isEmpty() ? "НЕМАЄ" : counts));
        System.out.println("Сума всіх заяв: " + analytics.totalFee(rows).toPlainString() + " UAH");
        System.out.println("Черга до звіту: " + pendingBefore);
        System.out.println("Черга після звіту: " + catalog.pendingCount());
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || !raw.matches(AMOUNT_PATTERN)) {
            throw new IllegalArgumentException("Некоректний формат суми.");
        }
        return new BigDecimal(raw);
    }

    private static int parseLimit(String raw) {
        if (raw == null || !raw.matches(LIMIT_PATTERN)) {
            throw new IllegalArgumentException("Некоректний формат ліміту.");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Переповнення ліміту.");
        }
    }

    private static String formatKeys(List<RequestRow> rows) {
        if (rows.isEmpty()) {
            return "ПОРОЖНЯ";
        }
        return rows.stream()
                .map(r -> r.requestId().value())
                .collect(Collectors.joining(", "));
    }

    private static RequestAnalytics createAnalytics() {
        return new RequestAnalytics(new RequestProcessor(new StandardFeePolicy()));
    }

    private static RequestCatalog<AcademicRequest> createDemoCatalog() {
        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        for (AcademicRequest request : demoRequests()) {
            catalog.add(request);
        }
        return catalog;
    }

    private static List<AcademicRequest> demoRequests() {
        StudentId first = new StudentId("ST-2025-0042");
        StudentId second = new StudentId("ST-2025-0007");
        StudentId third = new StudentId("ST-2025-0099");
        return List.of(
                new TranscriptRequest("REQ-003", first, "uk", 2),
                new EnrollmentRequest("REQ-001", second, "CS205"),
                new TranscriptRequest("REQ-002", second, "en", 1),
                new EnrollmentRequest("REQ-004", first, "MA101"),
                new TranscriptRequest("REQ-005", third, "en", 2),
                new TranscriptRequest("REQ-006", third, "uk", 3));
    }
}