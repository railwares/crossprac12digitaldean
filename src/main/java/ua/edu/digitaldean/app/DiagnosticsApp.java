package ua.edu.digitaldean.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.DiagnosticLog;
import ua.edu.digitaldean.service.DuplicateRequestException;
import ua.edu.digitaldean.service.ImportReport;
import ua.edu.digitaldean.service.RequestAnalytics;
import ua.edu.digitaldean.service.RequestCatalog;
import ua.edu.digitaldean.service.RequestDataException;
import ua.edu.digitaldean.service.RequestImporter;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class DiagnosticsApp {
    private static final String DEMO = """
            REQ-003;ST-2025-0042;uk;2
            REQ-002;ST-2025-0007;en;1
            REQ-006;ST-2025-0099;uk;3
            """;

    private static final Set<String> ALLOWED_LEVELS = Set.of(
            "FINE", "INFO", "WARNING", "SEVERE", "OFF");

    private record Parameters(Level level, String mode, Path path, String resourceSubmode) {
    }

    private static final class AuditProbe implements AutoCloseable {
        private final String name;
        private final List<String> closed;
        private final boolean failOnClose;

        private AuditProbe(String name, List<String> closed, boolean failOnClose) {
            this.name = name;
            this.closed = closed;
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            closed.add(name);
            if (failOnClose) {
                throw new IOException("Закриття " + name);
            }
        }
    }

    private DiagnosticsApp() {
    }

    public static void main(String[] args) {
        Parameters parameters;
        try {
            parameters = parseArguments(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Помилка: некоректні аргументи.");
            System.exit(2);
            return;
        }

        if (parameters.mode().equals("--resources")) {
            demonstrateResources(parameters.resourceSubmode());
            return;
        }

        Logger logger = DiagnosticLog.create(parameters.level());
        int code;
        try {
            if (parameters.mode().equals("--tolerant")) {
                code = importTolerant(parameters, logger);
            } else {
                code = importStrict(parameters, logger);
            }
        } finally {
            for (Handler handler : logger.getHandlers()) {
                handler.close();
                logger.removeHandler(handler);
            }
        }
        if (code != 0) {
            System.exit(code);
        }
    }

    private static Parameters parseArguments(String[] args) {
        int index = 0;
        Level level = Level.INFO;

        if (args.length > 0 && args[0].equals("--level")) {
            if (args.length < 2 || !ALLOWED_LEVELS.contains(args[1])) {
                throw new IllegalArgumentException("Некоректний рівень журналу.");
            }
            level = Level.parse(args[1]);
            index = 2;
        }

        int rem = args.length - index;
        if (rem == 0) {
            return new Parameters(level, "--demo", null, null);
        }

        String cmd = args[index];
        if (cmd.equals("--demo")) {
            if (rem != 1) {
                throw new IllegalArgumentException("Зайві аргументи для --demo.");
            }
            return new Parameters(level, "--demo", null, null);
        }

        if (cmd.equals("--resources")) {
            if (rem == 1) {
                return new Parameters(level, "--resources", null, "body");
            }
            if (rem == 2) {
                String sub = args[index + 1];
                if (sub.equals("body") || sub.equals("close-only") || sub.equals("ok")) {
                    return new Parameters(level, "--resources", null, sub);
                }
            }
            throw new IllegalArgumentException("Некоректний режим --resources.");
        }

        if (cmd.equals("--file") || cmd.equals("--tolerant")) {
            if (rem != 2 || args[index + 1] == null || args[index + 1].isBlank()) {
                throw new IllegalArgumentException("Очікується непорожній шлях до файлу.");
            }
            return new Parameters(level, cmd, Path.of(args[index + 1]), null);
        }

        throw new IllegalArgumentException("Невідомий режим.");
    }

    private static List<TranscriptRequest> loadStrict(Parameters parameters, RequestImporter importer)
            throws IOException, RequestDataException {
        if (parameters.mode().equals("--file")) {
            return importer.read(parameters.path());
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(DEMO))) {
            return importer.read(reader);
        }
    }

    private static int importStrict(Parameters parameters, Logger logger) {
        logger.fine("IMPORT_START");
        RequestImporter importer = new RequestImporter();
        try {
            List<TranscriptRequest> requests = loadStrict(parameters, importer);
            RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
            importer.registerUnique(requests, catalog);

            RequestAnalytics analytics = new RequestAnalytics(
                    new RequestProcessor(new StandardFeePolicy()));
            var rows = analytics.project(catalog.snapshot());

            logger.info("IMPORT_OK read=" + requests.size() + " registered=" + catalog.size());
            System.out.println("Прочитано заяв: " + requests.size());
            System.out.println("Зареєстровано заяв: " + catalog.size());
            System.out.println("Сума зборів: " + analytics.totalFee(rows).toPlainString() + " UAH");
            System.out.println("Заяв у черзі: " + catalog.pendingCount());
            return 0;
        } catch (DuplicateRequestException exception) {
            logger.log(Level.WARNING, "IMPORT_DUPLICATE_ERROR", exception);
            System.err.println("Помилка: повторний ідентифікатор заяви.");
            return 6;
        } catch (RequestDataException exception) {
            logger.log(Level.WARNING, "IMPORT_DATA_ERROR", exception);
            System.err.println("Помилка даних: рядок " + exception.lineNumber() + ".");
            return 3;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "IMPORT_IO_ERROR", exception);
            System.err.println("Помилка введення-виведення.");
            return 4;
        }
    }

    private static int importTolerant(Parameters parameters, Logger logger) {
        logger.fine("IMPORT_START");
        RequestImporter importer = new RequestImporter();
        try {
            ImportReport report;
            try (BufferedReader reader = Files.newBufferedReader(
                    parameters.path(), StandardCharsets.UTF_8)) {
                report = importer.readAll(reader);
            }

            RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
            importer.registerUnique(report.accepted(), catalog);

            for (RequestDataException rejected : report.rejected()) {
                logger.log(Level.WARNING,
                        "IMPORT_LINE_REJECTED line=" + rejected.lineNumber(),
                        rejected);
            }

            RequestAnalytics analytics = new RequestAnalytics(
                    new RequestProcessor(new StandardFeePolicy()));
            var rows = analytics.project(catalog.snapshot());

            logger.info("IMPORT_OK read=" + report.accepted().size()
                    + " registered=" + catalog.size()
                    + " rejected=" + report.rejected().size());

            System.out.println("Прочитано заяв: " + report.accepted().size());
            System.out.println("Зареєстровано заяв: " + catalog.size());
            System.out.println("Відхилено рядків: " + report.rejected().size());
            System.out.println("Сума зборів: " + analytics.totalFee(rows).toPlainString() + " UAH");
            System.out.println("Заяв у черзі: " + catalog.pendingCount());

            return report.rejected().isEmpty() ? 0 : 5;
        } catch (DuplicateRequestException exception) {
            logger.log(Level.WARNING, "IMPORT_DUPLICATE_ERROR", exception);
            System.err.println("Помилка: повторний ідентифікатор заяви.");
            return 6;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "IMPORT_IO_ERROR", exception);
            System.err.println("Помилка введення-виведення.");
            return 4;
        }
    }

    @SuppressWarnings("try")
    private static void demonstrateResources(String submode) {
        List<String> closed = new ArrayList<>();
        boolean failClose = !submode.equals("ok");
        boolean failBody = submode.equals("body");

        try (AuditProbe first = new AuditProbe("A", closed, failClose);
             AuditProbe second = new AuditProbe("B", closed, failClose)) {
            if (failBody) {
                throw new IOException("Помилка тіла");
            }
        } catch (IOException exception) {
            System.out.println("Закриття ресурсів: " + String.join(", ", closed));
            System.out.println("Основний виняток: " + exception.getClass().getSimpleName());
            System.out.println("Повідомлення: " + exception.getMessage());
            System.out.println("Пригнічених: " + exception.getSuppressed().length);
            for (Throwable suppressed : exception.getSuppressed()) {
                System.out.println("Пригнічений: " + suppressed.getMessage());
            }
            return;
        }

        System.out.println("Закриття ресурсів: " + String.join(", ", closed));
        System.out.println("Основний виняток: НЕМАЄ");
        System.out.println("Пригнічених: 0");
    }
}