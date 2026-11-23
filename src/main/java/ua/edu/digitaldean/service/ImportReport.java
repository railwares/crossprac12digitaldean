package ua.edu.digitaldean.service;

import java.util.List;
import java.util.Objects;
import ua.edu.digitaldean.model.TranscriptRequest;

public record ImportReport(
        List<TranscriptRequest> accepted,
        List<RequestDataException> rejected) {

    public ImportReport {
        accepted = List.copyOf(Objects.requireNonNull(
                accepted, "Список прийнятих заяв не може бути null."));
        rejected = List.copyOf(Objects.requireNonNull(
                rejected, "Список відхилених рядків не може бути null."));
    }
}