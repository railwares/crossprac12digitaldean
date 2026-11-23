package ua.edu.digitaldean.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.RequestKey;
import ua.edu.digitaldean.model.StudentId;

public final class RequestCatalog<T extends AcademicRequest> {
    private final Map<RequestKey, T> byId = new LinkedHashMap<>();
    private final Queue<RequestKey> pending = new ArrayDeque<>();

    public boolean add(T request) {
        Objects.requireNonNull(request, "Заява не може бути null.");
        RequestKey key = new RequestKey(request.requestId());
        if (byId.putIfAbsent(key, request) != null) {
            return false;
        }
        pending.offer(key);
        return true;
    }

    public int addAll(Collection<? extends T> source) {
        Objects.requireNonNull(source, "Колекція джерела не може бути null.");
        for (T item : source) {
            if (item == null) {
                throw new NullPointerException("Елемент колекції не може бути null.");
            }
        }

        int addedCount = 0;
        for (T item : source) {
            if (add(item)) {
                addedCount++;
            }
        }
        return addedCount;
    }

    public void copyTo(Collection<? super T> destination) {
        Objects.requireNonNull(destination, "Колекція приймача не може бути null.");
        destination.addAll(byId.values());
    }

    public Optional<T> find(RequestKey key) {
        Objects.requireNonNull(key, "Ключ пошуку не може бути null.");
        return Optional.ofNullable(byId.get(key));
    }

    public List<T> snapshot() {
        return List.copyOf(byId.values());
    }

    public Set<StudentId> studentIds() {
        LinkedHashSet<StudentId> ids = byId.values().stream()
                .map(AcademicRequest::studentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(ids);
    }

    public List<RequestKey> keysSorted() {
        return byId.keySet().stream().sorted().toList();
    }

    public List<T> select(Predicate<? super T> filter, Comparator<? super T> order) {
        Objects.requireNonNull(filter, "Умова відбору не може бути null.");
        Objects.requireNonNull(order, "Компаратор не може бути null.");
        return byId.values().stream().filter(filter).sorted(order).toList();
    }

    public List<T> select(Predicate<? super T> filter, Comparator<? super T> order, int limit) {
        Objects.requireNonNull(filter, "Умова відбору не може бути null.");
        Objects.requireNonNull(order, "Компаратор не може бути null.");
        if (limit < 0) {
            throw new IllegalArgumentException("Ліміт не може бути від'ємним.");
        }
        if (limit == 0) {
            return List.of();
        }
        return byId.values().stream().filter(filter).sorted(order).limit(limit).toList();
    }

    public Optional<T> pollPending() {
        RequestKey key = pending.poll();
        return key == null ? Optional.empty() : Optional.of(byId.get(key));
    }

    public List<T> processNext(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Ліміт не може бути від'ємним.");
        }
        if (limit == 0) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Optional<T> next = pollPending();
            if (next.isEmpty()) {
                break;
            }
            result.add(next.get());
        }
        return List.copyOf(result);
    }

    public Map<StudentId, List<T>> groupByStudent() {
        Map<StudentId, List<T>> rawGroups = byId.values().stream()
                .collect(Collectors.groupingBy(
                        AcademicRequest::studentId,
                        () -> new TreeMap<>(Comparator.comparing(StudentId::value)),
                        Collectors.collectingAndThen(
                                Collectors.toCollection(ArrayList::new),
                                list -> {
                                    list.sort(Comparator.comparing(r -> new RequestKey(r.requestId())));
                                    return List.copyOf(list);
                                }
                        )
                ));
        return Collections.unmodifiableMap(rawGroups);
    }

    public Map<String, Long> countByType() {
        Map<String, Long> counts = byId.values().stream()
                .collect(Collectors.groupingBy(
                        AcademicRequest::type,
                        TreeMap::new,
                        Collectors.counting()
                ));
        return Collections.unmodifiableMap(counts);
    }

    public int size() {
        return byId.size();
    }

    public int pendingCount() {
        return pending.size();
    }
}