package ua.edu.digitaldean.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.RequestKey;
import ua.edu.digitaldean.model.TranscriptRequest;

public final class RequestExchangeService {
    private final RequestGateway gateway;
    private final RequestProcessor processor;

    public RequestExchangeService(RequestGateway gateway, RequestProcessor processor) {
        this.gateway = Objects.requireNonNull(gateway, "Шлюз даних не може бути null.");
        this.processor = Objects.requireNonNull(processor, "Обробник заяв не може бути null.");
    }

    public ExchangeSummary execute(LaunchOptions options) throws IOException {
        Objects.requireNonNull(options, "Параметри запуску не можуть бути null.");
        if (options.mode().equals("--version")) {
            throw new IllegalArgumentException("Режим --version не виконується сервісом обміну.");
        }

        List<RequestFileRow> rawRows = options.input() == null
                ? gateway.readResource()
                : gateway.read(options.input(), options.sourceFormat());
        List<RequestFileRow> rows = checkedUnique(rawRows);

        if (options.output() != null) {
            gateway.write(options.output(), options.targetFormat(), rows);
        }

        RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
        List<TranscriptRequest> requests = rows.stream()
                .map(RequestFileRow::toRequest)
                .toList();
        for (TranscriptRequest request : requests) {
            if (!catalog.add(request)) {
                throw new IllegalStateException("Повторення мало бути відхилене раніше.");
            }
        }

        var results = processor.processAll(requests);
        return new ExchangeSummary(
                rows.size(),
                catalog.size(),
                processor.totalFee(results),
                catalog.pendingCount());
    }

    private static List<RequestFileRow> checkedUnique(List<RequestFileRow> rows)
            throws ExchangeFormatException {
        Objects.requireNonNull(rows, "Список рядків не може бути null.");
        List<RequestFileRow> snapshot = List.copyOf(rows);
        Set<RequestKey> keys = new HashSet<>();
        for (RequestFileRow row : snapshot) {
            if (!keys.add(new RequestKey(row.requestId()))) {
                throw new ExchangeFormatException("Повторний ключ заяви.");
            }
        }
        return snapshot;
    }
}