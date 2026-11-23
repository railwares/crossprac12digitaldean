package ua.edu.digitaldean.service;

import java.util.Locale;
import java.util.Objects;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.DormitoryRequest;
import ua.edu.digitaldean.model.EnrollmentRequest;
import ua.edu.digitaldean.model.StudentId;
import ua.edu.digitaldean.model.TranscriptRequest;

public final class DelimitedRequestFactory implements RequestFactory {
    public DelimitedRequestFactory() {
    }
    @Override
    public AcademicRequest create(StudentId studentId, String specification) {
        Objects.requireNonNull(studentId, "Ідентифікатор студента не може бути null.");
        if (specification == null || specification.isBlank()) {
            throw new IllegalArgumentException(
                    "Специфікація заяви не може бути порожньою.");
        }

        String[] parts = specification.strip().split(":", -1);
        String type = parts[0].strip().toUpperCase(Locale.ROOT);

        return switch (type) {
            case "ENROLLMENT" -> {
                if (parts.length != 3) {
                    throw new IllegalArgumentException(
                            "Формат ENROLLMENT: ENROLLMENT:<requestId>:<courseCode>.");
                }
                yield new EnrollmentRequest(parts[1], studentId, parts[2]);
            }
            case "TRANSCRIPT" -> {
                if (parts.length != 4) {
                    throw new IllegalArgumentException(
                            "Формат TRANSCRIPT: TRANSCRIPT:<requestId>:<language>:<copies>.");
                }
                int copies = Integer.parseInt(parts[3].strip());
                yield new TranscriptRequest(parts[1], studentId, parts[2], copies);
            }
            case "DORMITORY" -> {
                if (parts.length != 4) {
                    throw new IllegalArgumentException(
                            "Формат DORMITORY: DORMITORY:<requestId>:<building>:<months>.");
                }
                int months = Integer.parseInt(parts[3].strip());
                yield new DormitoryRequest(parts[1], studentId, parts[2], months);
            }
            default -> throw new IllegalArgumentException(
                    "Невідомий тип заяви: " + parts[0].strip() + ".");
        };
    }
}