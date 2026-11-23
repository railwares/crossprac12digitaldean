package ua.edu.digitaldean.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import ua.edu.digitaldean.service.ExchangeFormatException;
import ua.edu.digitaldean.service.ExchangeSummary;
import ua.edu.digitaldean.service.LaunchOptions;
import ua.edu.digitaldean.service.RequestExchangeService;

public final class ConsoleApplication {
    private final RequestExchangeService service;
    private final SummaryPrinter printer;

    public ConsoleApplication(RequestExchangeService service, SummaryPrinter printer) {
        this.service = Objects.requireNonNull(service, "Сервіс обміну не може бути null.");
        this.printer = Objects.requireNonNull(printer, "Принтер підсумку не може бути null.");
    }

    public int run(String[] args, PrintStream out, PrintStream err,
                   Supplier<Logger> loggerFactory) {
        Objects.requireNonNull(args, "Масив аргументів не може бути null.");
        Objects.requireNonNull(out, "Потік результату не може бути null.");
        Objects.requireNonNull(err, "Потік діагностики не може бути null.");
        Objects.requireNonNull(loggerFactory, "Фабрика журналу не може бути null.");

        LaunchOptions options;
        try {
            options = CommandLine.parse(args);
        } catch (IllegalArgumentException exception) {
            err.println("Помилка: некоректні аргументи.");
            return 2;
        }

        if (options.mode().equals("--version")) {
            return printVersion(out, err);
        }

        Logger logger = Objects.requireNonNull(
                loggerFactory.get(), "Фабрика має повернути журнал.");
        try {
            ExchangeSummary summary = service.execute(options);
            logger.info("EXCHANGE_OK count=" + summary.readCount());
            printer.print(summary, out);
            return 0;
        } catch (ExchangeFormatException exception) {
            logger.log(Level.WARNING, "EXCHANGE_DATA_ERROR", exception);
            err.println("Помилка даних CSV або JSON.");
            return 3;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "EXCHANGE_IO_ERROR", exception);
            err.println("Помилка читання або запису.");
            return 4;
        } finally {
            for (Handler handler : logger.getHandlers()) {
                handler.close();
                logger.removeHandler(handler);
            }
        }
    }

    private static int printVersion(PrintStream out, PrintStream err) {
        try (InputStream input = ConsoleApplication.class.getResourceAsStream("/build-info.properties")) {
            if (input == null) {
                err.println("Помилка: відсутні або некоректні відомості про версію.");
                return 5;
            }
            Properties props = new Properties();
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            String appName = props.getProperty("app.name");
            String appVersion = props.getProperty("app.version");
            String javaRelease = props.getProperty("java.release");
            if (isInvalidMeta(appName) || isInvalidMeta(appVersion) || isInvalidMeta(javaRelease)) {
                err.println("Помилка: відсутні або некоректні відомості про версію.");
                return 5;
            }
            out.println(appName.strip());
            out.println("Версія: " + appVersion.strip());
            out.println("Цільова версія Java: " + javaRelease.strip());
            return 0;
        } catch (IOException e) {
            err.println("Помилка: відсутні або некоректні відомості про версію.");
            return 5;
        }
    }

    private static boolean isInvalidMeta(String value) {
        return value == null || value.isBlank() || value.contains("${");
    }
}