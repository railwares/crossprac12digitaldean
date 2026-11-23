package ua.edu.digitaldean.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.EnrollmentRequest;
import ua.edu.digitaldean.model.RequestKey;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.RequestAnalytics;
import ua.edu.digitaldean.service.RequestCatalog;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.RequestRow;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class RequestAnalyticsTest {
    private static final StudentId A = new StudentId("ST-2025-0042");
    private static final StudentId B = new StudentId("ST-2025-0007");
    private static final StudentId C = new StudentId("ST-2025-0099");

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ВИКОНАННЯ API-СЦЕНАРІЇВ РОЗДІЛУ 9.2 (ПРАКТИЧНА №8) ");
        System.out.println("==================================================");

        runTest10();
        runTest11();
        runTest12();
        runTest13();
        runTest14();
        runTest15();
        runTest16();
        runTest17_18_19_Averages();
        runTest20();
        runTest21();
        runTest22();
        runTest23();

        System.out.println("\n--------------------------------------------------");
        System.out.println("Усі API-тести практичної роботи №8 успішно пройдено!");
    }

    private static RequestCatalog<AcademicRequest> initCatalog() {
        RequestCatalog<AcademicRequest> cat = new RequestCatalog<>();
        cat.add(new TranscriptRequest("REQ-003", A, "uk", 2));
        cat.add(new EnrollmentRequest("REQ-001", B, "CS205"));
        cat.add(new TranscriptRequest("REQ-002", B, "en", 1));
        cat.add(new EnrollmentRequest("REQ-004", A, "MA101"));
        cat.add(new TranscriptRequest("REQ-005", C, "en", 2));
        cat.add(new TranscriptRequest("REQ-006", C, "uk", 3));
        return cat;
    }

    private static RequestAnalytics initAnalytics() {
        return new RequestAnalytics(new RequestProcessor(new StandardFeePolicy()));
    }

    private static List<RequestRow> demoRows() {
        return initAnalytics().project(initCatalog().snapshot());
    }

    private static List<String> keys(List<RequestRow> rows) {
        return rows.stream().map(r -> r.requestId().value()).toList();
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

    private static void runTest10() {
        printHeader("10", "Складений select з множиною {A, C} та порожньою множиною",
                "analytics.select(rows, Set.of(A, C), 0.00, 80.00, 10)");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = demoRows();

        List<RequestRow> sel1 = an.select(rows, Set.of(A, C), new BigDecimal("0.00"), new BigDecimal("80.00"), 10);
        check(keys(sel1).equals(List.of("REQ-003", "REQ-005", "REQ-004")), "Вибірка {A, C} 0..80");
        check(an.totalFee(sel1).equals(new BigDecimal("160.00")), "Сума 160.00");

        List<RequestRow> sel2 = an.select(rows, Set.of(), new BigDecimal("0.00"), new BigDecimal("200.00"), 10);
        check(sel2.size() == 6, "Порожня множина означає всіх студентів (6 заяв)");

        System.out.println("  * Вибірка {A, C} (0..80): " + keys(sel1) + ", сума: " + an.totalFee(sel1));
        System.out.println("  * Вибірка з порожньою множиною (0..200): " + keys(sel2));
        printPassed();
    }

    private static void runTest11() {
        printHeader("11", "Порожній список rows для всіх нових методів",
                "select/totalsByStudent/countByTypeAndStudent/partitionByFee/firstForStudent(List.of())");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> empty = List.of();

        check(an.select(empty, Set.of(A), BigDecimal.ZERO, BigDecimal.TEN, 5).isEmpty(), "select порожній");
        check(an.selectPrefixThenSort(empty, BigDecimal.ZERO, 5).isEmpty(), "selectPrefixThenSort порожній");
        check(an.totalsByStudent(empty).isEmpty(), "totalsByStudent порожній");
        check(an.countByTypeAndStudent(empty).isEmpty(), "countByTypeAndStudent порожній");

        Map<Boolean, List<RequestRow>> part = an.partitionByFee(empty, BigDecimal.TEN);
        check(part.keySet().stream().toList().equals(List.of(false, true)), "partition ключі false, true");
        check(part.get(false).isEmpty() && part.get(true).isEmpty(), "обидва списки порожні");
        check(an.firstForStudent(empty, A).isEmpty(), "firstForStudent Optional.empty()");

        System.out.println("  * Усі вибірки та карти порожні, partitionByFee містить {false=[], true=[]}");
        printPassed();
    }

    private static void runTest12() {
        printHeader("12", "totalsByStudent на повному наборі та нульових рядках",
                "analytics.totalsByStudent(rows)");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = demoRows();

        Map<StudentId, BigDecimal> totals = an.totalsByStudent(rows);
        List<StudentId> order = new ArrayList<>(totals.keySet());
        check(order.equals(List.of(B, A, C)), "Порядок студентів B, A, C");
        check(totals.get(B).equals(new BigDecimal("40.00")), "B = 40.00");
        check(totals.get(A).equals(new BigDecimal("80.00")), "A = 80.00");
        check(totals.get(C).equals(new BigDecimal("200.00")), "C = 200.00");

        List<RequestRow> zeroRows = rows.stream()
                .filter(r -> r.fee().signum() == 0).toList();
        Map<StudentId, BigDecimal> zeroTotals = an.totalsByStudent(zeroRows);
        check(zeroTotals.get(B).equals(new BigDecimal("0.00")), "B нульовий = 0.00");
        check(zeroTotals.get(A).equals(new BigDecimal("0.00")), "A нульовий = 0.00");

        System.out.println("  * Суми за студентами: " + totals);
        System.out.println("  * Суми для нульових рядків (REQ-001, REQ-004): " + zeroTotals);
        printPassed();
    }

    private static void runTest13() {
        printHeader("13", "countByTypeAndStudent дворівневе групування",
                "analytics.countByTypeAndStudent(rows)");

        RequestAnalytics an = initAnalytics();
        Map<String, Map<StudentId, Long>> map = an.countByTypeAndStudent(demoRows());

        List<String> types = new ArrayList<>(map.keySet());
        check(types.equals(List.of("Академічна довідка", "Зарахування на дисципліну")), "Порядок типів");

        Map<StudentId, Long> trans = map.get("Академічна довідка");
        check(new ArrayList<>(trans.keySet()).equals(List.of(B, A, C)), "Порядок студентів у довідках");
        check(trans.get(B) == 1L && trans.get(A) == 1L && trans.get(C) == 2L, "Лічильники довідок");

        Map<StudentId, Long> enroll = map.get("Зарахування на дисципліну");
        check(new ArrayList<>(enroll.keySet()).equals(List.of(B, A)), "Порядок студентів у зарахуваннях");
        check(enroll.get(B) == 1L && enroll.get(A) == 1L, "Лічильники зарахувань");

        System.out.println("  * Дворівневе групування: " + map);
        printPassed();
    }

    private static void runTest14() {
        printHeader("14", "partitionByFee з порогом 80.00",
                "analytics.partitionByFee(rows, 80.00)");

        RequestAnalytics an = initAnalytics();
        Map<Boolean, List<RequestRow>> part = an.partitionByFee(demoRows(), new BigDecimal("80.00"));

        check(new ArrayList<>(part.keySet()).equals(List.of(false, true)), "Порядок ключів false, true");
        check(keys(part.get(false)).equals(List.of("REQ-001", "REQ-002", "REQ-004")), "Група false");
        check(keys(part.get(true)).equals(List.of("REQ-003", "REQ-005", "REQ-006")), "Група true");

        System.out.println("  * false (< 80.00): " + keys(part.get(false)));
        System.out.println("  * true (>= 80.00): " + keys(part.get(true)));
        printPassed();
    }

    private static void runTest15() {
        printHeader("15", "partitionByFee з порогом 0.00",
                "analytics.partitionByFee(rows, 0.00)");

        RequestAnalytics an = initAnalytics();
        Map<Boolean, List<RequestRow>> part = an.partitionByFee(demoRows(), new BigDecimal("0.00"));

        check(part.get(false).isEmpty(), "Група false порожня");
        check(keys(part.get(true)).equals(List.of("REQ-001", "REQ-002", "REQ-003", "REQ-004", "REQ-005", "REQ-006")),
                "Група true відсортована за ключем");

        System.out.println("  * false (< 0.00): " + keys(part.get(false)));
        System.out.println("  * true (>= 0.00): " + keys(part.get(true)));
        printPassed();
    }

    private static void runTest16() {
        printHeader("16", "firstForStudent для A, B, C та відсутнього студента",
                "analytics.firstForStudent(rows, student)");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = demoRows();

        check(an.firstForStudent(rows, A).orElseThrow().requestId().value().equals("REQ-003"), "Перша для A: REQ-003");
        check(an.firstForStudent(rows, B).orElseThrow().requestId().value().equals("REQ-001"), "Перша для B: REQ-001");
        check(an.firstForStudent(rows, C).orElseThrow().requestId().value().equals("REQ-005"), "Перша для C: REQ-005");
        check(an.firstForStudent(rows, new StudentId("ST-2025-9999")).isEmpty(), "Для 9999 порожній Optional");

        System.out.println("  * A -> REQ-003, B -> REQ-001, C -> REQ-005, ST-2025-9999 -> Optional.empty");
        printPassed();
    }

    private static void runTest17_18_19_Averages() {
        printHeader("17-19 (API)", "Експеримент sorted->limit проти limit->sorted (середні значення)",
                "select vs selectPrefixThenSort для limit = 3, 2, 0");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = demoRows();
        BigDecimal min = new BigDecimal("40.00");

        // Limit 3
        List<RequestRow> s3 = an.select(rows, min, 3);
        List<RequestRow> p3 = an.selectPrefixThenSort(rows, min, 3);
        check(an.averageFee(s3).orElseThrow().equals(new BigDecimal("93.33")), "Середнє s3 = 93.33");
        check(an.averageFee(p3).orElseThrow().equals(new BigDecimal("66.67")), "Середнє p3 = 66.67");

        // Limit 2
        List<RequestRow> s2 = an.select(rows, min, 2);
        List<RequestRow> p2 = an.selectPrefixThenSort(rows, min, 2);
        check(an.averageFee(s2).orElseThrow().equals(new BigDecimal("100.00")), "Середнє s2 = 100.00");
        check(an.averageFee(p2).orElseThrow().equals(new BigDecimal("60.00")), "Середнє p2 = 60.00");

        // Limit 0
        check(an.averageFee(an.select(rows, min, 0)).isEmpty(), "Середнє s0 відсутнє");
        check(an.averageFee(an.selectPrefixThenSort(rows, min, 0)).isEmpty(), "Середнє p0 відсутнє");

        System.out.println("  * Limit 3 -> середні: 93.33 UAH (sorted->limit) vs 66.67 UAH (limit->sorted)");
        System.out.println("  * Limit 2 -> середні: 100.00 UAH (sorted->limit) vs 60.00 UAH (limit->sorted)");
        System.out.println("  * Limit 0 -> обидва середні Optional.empty()");
        printPassed();
    }

    private static void runTest20() {
        printHeader("20", "Перевірка контрактів помилок (NPE та IAE до початку конвеєра)",
                "виклики з null, null-елементами, min > max, threshold < 0, limit < 0");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> empty = List.of();

        try { an.select(null, Set.of(), BigDecimal.ZERO, BigDecimal.ONE, 1); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { an.select(Arrays.asList((RequestRow) null), Set.of(), BigDecimal.ZERO, BigDecimal.ONE, 0); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { Set<StudentId> s = new HashSet<>(); s.add(null); an.select(empty, s, BigDecimal.ZERO, BigDecimal.ONE, 0); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { an.select(empty, Set.of(), new BigDecimal("81"), new BigDecimal("80"), 0); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { an.partitionByFee(empty, new BigDecimal("-0.01")); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { an.selectPrefixThenSort(empty, BigDecimal.ZERO, -1); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        try { an.firstForStudent(empty, null); throw new AssertionError(); } catch (NullPointerException expected) {}

        System.out.println("  * Усі некоректні виклики викинули очікувані NPE / IllegalArgumentException");
        printPassed();
    }

    private static void runTest21() {
        printHeader("21", "Заборона модифікації всіх рівнів повернутих структур",
                "спроби add/put у вибірки, карти груп, вкладені карти та списки розбиття");

        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = demoRows();
        RequestRow sample = rows.get(0);

        try { an.select(rows, Set.of(), BigDecimal.ZERO, new BigDecimal("200"), 5).add(sample); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { an.selectPrefixThenSort(rows, BigDecimal.ZERO, 5).add(sample); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { an.totalsByStudent(rows).put(A, BigDecimal.ZERO); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}

        Map<String, Map<StudentId, Long>> nested = an.countByTypeAndStudent(rows);
        try { nested.put("Новий", Map.of()); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { nested.values().iterator().next().put(A, 99L); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}

        Map<Boolean, List<RequestRow>> part = an.partitionByFee(rows, new BigDecimal("80.00"));
        try { part.put(false, List.of()); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { part.get(false).add(sample); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        try { part.get(true).add(sample); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}

        System.out.println("  * Усі спроби мутації зовнішніх і внутрішніх колекцій заблоковані (UnsupportedOperationException)");
        printPassed();
    }

    private static void runTest22() {
        printHeader("22", "Незалежність знімків після очищення вхідного списку та додавання REQ-007",
                "mutableRows.clear(); catalog.add(REQ-007)");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        RequestAnalytics an = initAnalytics();

        List<RequestRow> mutableRows = new ArrayList<>(an.project(cat.snapshot()));
        Map<StudentId, BigDecimal> oldTotals = an.totalsByStudent(mutableRows);
        Map<Boolean, List<RequestRow>> oldPart = an.partitionByFee(mutableRows, new BigDecimal("80.00"));

        mutableRows.clear();
        check(oldTotals.size() == 3, "Старі підсумки збережені після clear()");
        check(oldPart.get(true).size() == 3, "Старе розбиття збережене після clear()");

        cat.add(new TranscriptRequest("REQ-007", A, "uk", 1));
        List<RequestRow> newRows = an.project(cat.snapshot());
        Map<StudentId, BigDecimal> newTotals = an.totalsByStudent(newRows);

        check(oldTotals.get(A).equals(new BigDecimal("80.00")), "Стара сума A = 80.00");
        check(newTotals.get(A).equals(new BigDecimal("120.00")), "Нова сума A = 120.00");

        System.out.println("  * Старі структури не змінилися; нові структури відобразили додану REQ-007");
        printPassed();
    }

    private static void runTest23() {
        printHeader("23", "Збереження стану реєстру й черги після всіх аналітичних операцій",
                "перевірка size, snapshot, pendingCount та вичерпування черги");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        RequestAnalytics an = initAnalytics();
        List<RequestRow> rows = an.project(cat.snapshot());

        an.select(rows, Set.of(), BigDecimal.ZERO, new BigDecimal("200"), 10);
        an.selectPrefixThenSort(rows, new BigDecimal("40"), 3);
        an.totalsByStudent(rows);
        an.countByTypeAndStudent(rows);
        an.partitionByFee(rows, new BigDecimal("80"));
        an.firstForStudent(rows, A);

        check(cat.size() == 6 && cat.pendingCount() == 6, "Кількість заяв і черги = 6");

        List<String> drained = new ArrayList<>();
        Optional<AcademicRequest> next;
        while ((next = cat.pollPending()).isPresent()) {
            drained.add(next.get().requestId());
        }
        List<String> expectedOrder = List.of("REQ-003", "REQ-001", "REQ-002", "REQ-004", "REQ-005", "REQ-006");
        check(drained.equals(expectedOrder), "Порядок черги FIFO збережено");

        System.out.println("  * До вичерпування: size=6, pendingCount=6");
        System.out.println("  * Порядок вичерпаної черги: " + drained);
        printPassed();
    }
}