package ua.edu.digitaldean.service;

import java.math.BigDecimal;
import java.util.Objects;

public record ExchangeSummary(
        int readCount,
        int registeredCount,
        BigDecimal totalFee,
        int pendingCount) {

    public ExchangeSummary {
        if (readCount < 0 || registeredCount < 0 || pendingCount < 0) {
            throw new IllegalArgumentException("Лічильники не можуть бути від'ємними.");
        }
        Objects.requireNonNull(totalFee, "Сума зборів не може бути null.");
        if (totalFee.signum() < 0 || totalFee.scale() != 2) {
            throw new IllegalArgumentException("Сума зборів має бути невід'ємною з масштабом 2.");
        }
    }
}