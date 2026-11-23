package ua.edu.digitaldean.app;

import java.io.PrintStream;
import java.util.Objects;
import ua.edu.digitaldean.service.ExchangeSummary;

public final class SummaryPrinter {
    public SummaryPrinter() {
    }

    public void print(ExchangeSummary summary, PrintStream out) {
        Objects.requireNonNull(summary, "Підсумок не може бути null.");
        Objects.requireNonNull(out, "Потік виведення не може бути null.");
        out.println("Прочитано заяв: " + summary.readCount());
        out.println("Зареєстровано заяв: " + summary.registeredCount());
        out.println("Сума зборів: " + summary.totalFee().toPlainString() + " UAH");
        out.println("Заяв у черзі: " + summary.pendingCount());
    }
}