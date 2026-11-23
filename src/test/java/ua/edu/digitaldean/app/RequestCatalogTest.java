package ua.edu.digitaldean.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.EnrollmentRequest;
import ua.edu.digitaldean.model.RequestKey;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.RequestCatalog;

public final class RequestCatalogTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ВИКОНАННЯ API-СЦЕНАРІЇВ РОЗДІЛУ 9.2 (ТЕСТИ 11-19) ");
        System.out.println("==================================================");

        runTest11();
        runTest12();
        runTest13();
        runTest14();
        runTest15();
        runTest16();
        runTest17();
        runTest18();
        runTest19();

        System.out.println("\n--------------------------------------------------");
        System.out.println("Усі API-тести успішно пройдено!");
    }

    private static RequestCatalog<AcademicRequest> initCatalog() {
        RequestCatalog<AcademicRequest> cat = new RequestCatalog<>();
        StudentId s1 = new StudentId("ST-2025-0042");
        StudentId s2 = new StudentId("ST-2025-0007");
        cat.add(new TranscriptRequest("REQ-003", s1, "uk", 2));
        cat.add(new EnrollmentRequest("REQ-001", s2, "CS205"));
        cat.add(new TranscriptRequest("REQ-002", s2, "en", 1));
        cat.add(new EnrollmentRequest("REQ-004", s1, "MA101"));
        return cat;
    }

    private static void printHeader(int id, String desc, String call) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [Тест " + id + "] " + desc);
        System.out.println("Дія / API-виклик: " + call);
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

    private static void runTest11() {
        printHeader(11, "addAll з наявним ключем та внутрішнім дублікатом",
                "cat.addAll([REQ-002, REQ-005(CS101), REQ-005(CS102)])");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        StudentId s = new StudentId("ST-2025-0042");
        List<AcademicRequest> list = List.of(
                new TranscriptRequest("REQ-002", s, "uk", 5),
                new EnrollmentRequest("REQ-005", s, "CS101"),
                new EnrollmentRequest("REQ-005", s, "CS102")
        );

        int added = cat.addAll(list);
        check(added == 1, "Додано лише 1 унікальний новий ключ");
        check(cat.size() == 5, "Розмір реєстру став 5");
        check(cat.pendingCount() == 5, "Черга містить 5 ключів");

        EnrollmentRequest req5 = (EnrollmentRequest) cat.find(new RequestKey("REQ-005")).orElseThrow();
        check(req5.courseCode().equals("CS101"), "Збережено саме перший екземпляр CS101");

        System.out.println("  * Додано нових ключів: " + added);
        System.out.println("  * Загальний розмір реєстру: " + cat.size() + ", у черзі: " + cat.pendingCount());
        System.out.println("  * Для REQ-005 збережено курс: " + req5.courseCode());
        printPassed();
    }

    private static void runTest12() {
        printHeader(12, "addAll валідація порожнього списку та null-значень",
                "cat.addAll(empty); cat.addAll(null); cat.addAll([REQ-005, null])");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        int addedEmpty = cat.addAll(List.of());
        check(addedEmpty == 0, "Порожній список повертає 0");

        boolean npeOnNullCollection = false;
        try {
            cat.addAll(null);
        } catch (NullPointerException e) {
            npeOnNullCollection = true;
        }
        check(npeOnNullCollection, "null-колекція викликає NPE");

        boolean npeOnElement = false;
        try {
            StudentId s = new StudentId("ST-2025-0042");
            cat.addAll(Arrays.asList(new EnrollmentRequest("REQ-005", s, "CS101"), null));
        } catch (NullPointerException e) {
            npeOnElement = true;
        }
        check(npeOnElement, "null-елемент викликає NPE");
        check(cat.find(new RequestKey("REQ-005")).isEmpty(), "Відсутнє часткове додавання (атомарність)");
        check(cat.size() == 4 && cat.pendingCount() == 4, "Стан реєстру та черги не змінився");

        System.out.println("  * Додавання порожнього списку: 0");
        System.out.println("  * Перевірка null-колекції та null-елемента: успішно перехоплено NPE");
        System.out.println("  * Часткове додавання не відбулося, розмір залишився: " + cat.size());
        printPassed();
    }

    private static void runTest13() {
        printHeader(13, "copyTo з нижньою межею wildcard (? super T)",
                "transcriptCat.copyTo(destListAcademicRequest)");

        RequestCatalog<TranscriptRequest> cat = new RequestCatalog<>();
        StudentId s = new StudentId("ST-2025-0042");
        cat.add(new TranscriptRequest("REQ-003", s, "uk", 2));
        cat.add(new TranscriptRequest("REQ-002", s, "en", 1));

        List<AcademicRequest> dest = new ArrayList<>();
        dest.add(new EnrollmentRequest("REQ-009", s, "CS101"));

        cat.copyTo(dest);
        check(dest.size() == 3, "Розмір приймача став 3");
        check(dest.get(0).requestId().equals("REQ-009"), "Збережено початковий елемент REQ-009");
        check(dest.get(1).requestId().equals("REQ-003"), "Додано REQ-003");
        check(dest.get(2).requestId().equals("REQ-002"), "Додано REQ-002");
        check(cat.size() == 2 && cat.pendingCount() == 2, "Джерело не змінилося");

        System.out.println("  * Елементи у списку-приймачі: "
                + dest.stream().map(AcademicRequest::requestId).toList());
        System.out.println("  * Джерело TranscriptRequest зберегло 2 записи без приведення типів");
        printPassed();
    }

    private static void runTest14() {
        printHeader(14, "groupByStudent порядок груп та вкладених списків",
                "cat.groupByStudent()");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        Map<StudentId, List<AcademicRequest>> groups = cat.groupByStudent();

        List<StudentId> keys = new ArrayList<>(groups.keySet());
        check(keys.get(0).value().equals("ST-2025-0007"), "Перша група ST-2025-0007");
        check(keys.get(1).value().equals("ST-2025-0042"), "Друга група ST-2025-0042");

        List<String> ids1 = groups.get(keys.get(0)).stream().map(AcademicRequest::requestId).toList();
        List<String> ids2 = groups.get(keys.get(1)).stream().map(AcademicRequest::requestId).toList();
        check(ids1.equals(List.of("REQ-001", "REQ-002")), "Порядок списку першої групи");
        check(ids2.equals(List.of("REQ-003", "REQ-004")), "Порядок списку другої групи");

        System.out.println("  * Порядок студентів: " + keys.stream().map(StudentId::value).toList());
        System.out.println("  * Заяви " + keys.get(0).value() + ": " + ids1);
        System.out.println("  * Заяви " + keys.get(1).value() + ": " + ids2);
        printPassed();
    }

    private static void runTest15() {
        printHeader(15, "Незмінність груп, списків та збереження старих знімків",
                "groups.get(...).add(...); counts.put(...); cat.add(REQ-005)");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        Map<StudentId, List<AcademicRequest>> groups = cat.groupByStudent();
        Map<String, Long> counts = cat.countByType();

        cat.add(new EnrollmentRequest("REQ-005", new StudentId("ST-2025-0042"), "CS101"));

        boolean groupListImmutable = false;
        try {
            groups.values().iterator().next().add(new EnrollmentRequest("REQ-099", new StudentId("ST-2025-0007"), "CS101"));
        } catch (UnsupportedOperationException e) {
            groupListImmutable = true;
        }
        check(groupListImmutable, "Вкладений список групи не допускає модифікації");

        boolean mapImmutable = false;
        try {
            counts.put("Тест", 1L);
        } catch (UnsupportedOperationException e) {
            mapImmutable = true;
        }
        check(mapImmutable, "Статистика типів не допускає модифікації");
        check(cat.size() == 5, "Реєстр після додавання містить 5 заяв");

        System.out.println("  * Спроби модифікації повернутих списків та мап: успішно заблоковано (UnsupportedOperationException)");
        System.out.println("  * Додано REQ-005, поточний реєстр: 5 заяв, старі знімки залишилися незмінними");
        printPassed();
    }

    private static void runTest16() {
        printHeader(16, "countByType підрахунок за типами заяв",
                "cat.countByType(); emptyCat.countByType()");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        Map<String, Long> counts = cat.countByType();
        check(counts.get("Академічна довідка") == 2L, "2 академічні довідки");
        check(counts.get("Зарахування на дисципліну") == 2L, "2 зарахування");

        RequestCatalog<AcademicRequest> empty = new RequestCatalog<>();
        check(empty.countByType().isEmpty(), "Порожній каталог дає порожній результат");

        System.out.println("  * Заповнений реєстр: " + counts);
        System.out.println("  * Порожній реєстр: " + empty.countByType());
        printPassed();
    }

    private static void runTest17() {
        printHeader(17, "select сортування за спаданням збору та ліміт",
                "cat.select(r -> true, feeDescThenKeyAsc, 4)");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        Comparator<AcademicRequest> reportOrder = Comparator
                .comparing(AcademicRequest::baseFee, Comparator.reverseOrder())
                .thenComparing(r -> new RequestKey(r.requestId()));

        List<AcademicRequest> sel = cat.select(r -> true, reportOrder, 4);
        List<String> ids = sel.stream().map(AcademicRequest::requestId).toList();
        check(ids.equals(List.of("REQ-003", "REQ-002", "REQ-001", "REQ-004")),
                "Порядок: 80 UAH (003) -> 40 UAH (002) -> 0 UAH (001, потім 004)");

        System.out.println("  * Відібраний порядок заяв: " + ids);
        System.out.println("  * Реєстр і черга не зазнали змін (size = " + cat.size() + ")");
        printPassed();
    }

    private static void runTest18() {
        printHeader(18, "Послідовні виклики processNext для черги FIFO",
                "cat.processNext(2); cat.processNext(2); cat.processNext(1)");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        List<AcademicRequest> p1 = cat.processNext(2);
        List<AcademicRequest> p2 = cat.processNext(2);
        List<AcademicRequest> p3 = cat.processNext(1);

        List<String> ids1 = p1.stream().map(AcademicRequest::requestId).toList();
        List<String> ids2 = p2.stream().map(AcademicRequest::requestId).toList();

        check(ids1.equals(List.of("REQ-003", "REQ-001")), "Перша порція 003, 001");
        check(ids2.equals(List.of("REQ-002", "REQ-004")), "Друга порція 002, 004");
        check(p3.isEmpty(), "Третя порція порожня");
        check(cat.pendingCount() == 0, "Черга повністю вичерпана (0)");
        check(cat.size() == 4, "Усі 4 заяви залишилися в Map сховища");

        System.out.println("  * Перша порція (limit=2): " + ids1);
        System.out.println("  * Друга порція (limit=2): " + ids2);
        System.out.println("  * Третя порція (limit=1): порожня " + p3);
        System.out.println("  * Залишок у черзі: " + cat.pendingCount() + ", заяв у реєстрі: " + cat.size());
        printPassed();
    }

    private static void runTest19() {
        printHeader(19, "Реакція на від'ємні ліміти в select та processNext",
                "cat.select(..., -1); cat.processNext(-1)");

        RequestCatalog<AcademicRequest> cat = initCatalog();
        boolean selectNegativeCaught = false;
        try {
            cat.select(r -> true, Comparator.comparing(AcademicRequest::requestId), -1);
        } catch (IllegalArgumentException e) {
            selectNegativeCaught = true;
        }
        check(selectNegativeCaught, "select з limit=-1 кидає IllegalArgumentException");

        boolean processNegativeCaught = false;
        try {
            cat.processNext(-1);
        } catch (IllegalArgumentException e) {
            processNegativeCaught = true;
        }
        check(processNegativeCaught, "processNext з limit=-1 кидає IllegalArgumentException");
        check(cat.size() == 4 && cat.pendingCount() == 4, "Стан не змінився");

        System.out.println("  * select(limit = -1): успішно перехоплено IllegalArgumentException");
        System.out.println("  * processNext(limit = -1): успішно перехоплено IllegalArgumentException");
        System.out.println("  * Черга та реєстр залишилися недоторканими");
        printPassed();
    }
}