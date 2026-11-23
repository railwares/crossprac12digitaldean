package ua.edu.digitaldean.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import ua.edu.digitaldean.model.AcademicRequest;
import ua.edu.digitaldean.model.TranscriptRequest;
import ua.edu.digitaldean.service.DiagnosticLog;
import ua.edu.digitaldean.service.ExchangeFormatException;
import ua.edu.digitaldean.service.RequestCatalog;
import ua.edu.digitaldean.service.RequestFileRow;
import ua.edu.digitaldean.service.RequestFiles;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class FileExchangeApp {
    private static final String RESOURCE_NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*\\.csv";
    private static final String LIMIT_PATTERN = "0|[1-9][0-9]*";

    private record Parameters(
            Path baseDir,
            char delimiter,
            int maxBytes,
            String mode,
            String resourceName,
            String sourceFormat,
            Path input,
            String targetFormat,
            Path output) {
    }

    private FileExchangeApp() {
    }

    public static void main(String[] args) {
        Parameters parameters;
        try {
            parameters = parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Помилка: некоректні аргументи.");
            System.exit(2);
            return;
        }

        Logger logger = DiagnosticLog.create();
        int code;
        try {
            code = execute(parameters, logger);
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

    private static Parameters parse(String[] args) {
        int index = 0;
        Path baseDir = Path.of(".");
        char delimiter = ',';
        int maxBytes = RequestFiles.DEFAULT_MAX_BYTES;

        boolean seenBase = false;
        boolean seenDelimiter = false;
        boolean seenMaxBytes = false;

        while (index < args.length) {
            String token = args[index];
            if (token.equals("--base")) {
                if (seenBase || index + 1 >= args.length) {
                    throw new IllegalArgumentException("Некоректний префікс --base.");
                }
                String rawBase = args[index + 1];
                if (rawBase == null || rawBase.isBlank()) {
                    throw new IllegalArgumentException("Порожній базовий каталог.");
                }
                baseDir = Path.of(rawBase);
                seenBase = true;
                index += 2;
            } else if (token.equals("--delimiter")) {
                if (seenDelimiter || index + 1 >= args.length) {
                    throw new IllegalArgumentException("Некоректний префікс --delimiter.");
                }
                String rawDelim = args[index + 1];
                if ("comma".equals(rawDelim)) {
                    delimiter = ',';
                } else if ("semicolon".equals(rawDelim)) {
                    delimiter = ';';
                } else {
                    throw new IllegalArgumentException("Невідомий розділювач.");
                }
                seenDelimiter = true;
                index += 2;
            } else if (token.equals("--max-bytes")) {
                if (seenMaxBytes || index + 1 >= args.length) {
                    throw new IllegalArgumentException("Некоректний префікс --max-bytes.");
                }
                maxBytes = parseMaxBytes(args[index + 1]);
                seenMaxBytes = true;
                index += 2;
            } else {
                break;
            }
        }

        int rem = args.length - index;
        if (rem == 0) {
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--resource", "requests.csv", "csv", null, null, null);
        }

        String mode = args[index];
        if (mode.equals("--resource")) {
            if (rem != 1) {
                throw new IllegalArgumentException("Зайві аргументи для --resource.");
            }
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--resource", "requests.csv", "csv", null, null, null);
        }

        if (mode.equals("--seed")) {
            if (rem != 2) {
                throw new IllegalArgumentException("Очікується вихідний шлях для --seed.");
            }
            Path out = RequestFiles.resolveInside(baseDir, args[index + 1]);
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--seed", "requests.csv", "csv", null, "json", out);
        }

        if (mode.equals("--seed-resource")) {
            if (rem != 3) {
                throw new IllegalArgumentException("Очікується ім'я ресурсу та вихідний шлях.");
            }
            String resName = args[index + 1];
            if (resName == null || !resName.matches(RESOURCE_NAME_PATTERN)) {
                throw new IllegalArgumentException("Некоректне ім'я ресурсу.");
            }
            Path out = RequestFiles.resolveInside(baseDir, args[index + 2]);
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--seed-resource", resName, "csv", null, "json", out);
        }

        if (mode.equals("--inspect")) {
            if (rem != 3) {
                throw new IllegalArgumentException("Очікується формат і шлях для --inspect.");
            }
            String fmt = args[index + 1];
            RequestFiles.requireFormat(fmt);
            Path in = RequestFiles.resolveInside(baseDir, args[index + 2]);
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--inspect", null, fmt, in, null, null);
        }

        if (mode.equals("--convert")) {
            if (rem != 5) {
                throw new IllegalArgumentException("Очікується 4 параметри для --convert.");
            }
            String inFmt = args[index + 1];
            String outFmt = args[index + 3];
            RequestFiles.requireFormat(inFmt);
            RequestFiles.requireFormat(outFmt);
            Path in = RequestFiles.resolveInside(baseDir, args[index + 2]);
            Path out = RequestFiles.resolveInside(baseDir, args[index + 4]);
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--convert", null, inFmt, in, outFmt, out);
        }

        if (mode.equals("--probe")) {
            if (rem != 2) {
                throw new IllegalArgumentException("Очікується каталог для --probe.");
            }
            Path targetDir = RequestFiles.resolveInside(baseDir, args[index + 1]);
            return new Parameters(baseDir, delimiter, maxBytes,
                    "--probe", null, null, targetDir, null, null);
        }

        throw new IllegalArgumentException("Невідомий режим.");
    }

    private static int parseMaxBytes(String raw) {
        if (raw == null || !raw.matches(LIMIT_PATTERN)) {
            throw new IllegalArgumentException("Некоректний синтаксис ліміту байтів.");
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0 || value > RequestFiles.DEFAULT_MAX_BYTES) {
                throw new IllegalArgumentException("Ліміт байтів поза межами.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ліміт байтів поза межами int.");
        }
    }

    private static int execute(Parameters parameters, Logger logger) {
        RequestFiles files = new RequestFiles(parameters.maxBytes(), parameters.delimiter());
        try {
            if (parameters.mode().equals("--probe")) {
                RequestFiles.ProbeReport report = files.probe(parameters.input());
                logger.info("PROBE_OK");
                System.out.println("POSIX: " + report.posix());
                System.out.println("DOS: " + report.dos());
                System.out.println("Регістри: " + report.caseSensitivity());
                return 0;
            }

            List<RequestFileRow> rows = parameters.input() == null
                    ? files.readResource(parameters.resourceName())
                    : files.read(parameters.input(), parameters.sourceFormat());

            if (parameters.output() != null) {
                files.write(parameters.output(), parameters.targetFormat(), rows);
            }

            RequestCatalog<AcademicRequest> catalog = new RequestCatalog<>();
            List<TranscriptRequest> requests = rows.stream()
                    .map(RequestFileRow::toRequest).toList();
            for (TranscriptRequest request : requests) {
                if (!catalog.add(request)) {
                    throw new IllegalStateException("Повторення мало бути відхилене раніше.");
                }
            }

            RequestProcessor processor = new RequestProcessor(new StandardFeePolicy());
            var results = processor.processAll(requests);

            logger.info("EXCHANGE_OK count=" + rows.size());
            System.out.println("Прочитано заяв: " + rows.size());
            System.out.println("Зареєстровано заяв: " + catalog.size());
            System.out.println("Сума зборів: " + processor.totalFee(results).toPlainString() + " UAH");
            System.out.println("Заяв у черзі: " + catalog.pendingCount());
            return 0;
        } catch (ExchangeFormatException exception) {
            logger.log(Level.WARNING, "EXCHANGE_DATA_ERROR", exception);
            System.err.println("Помилка даних CSV або JSON.");
            return 3;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "EXCHANGE_IO_ERROR", exception);
            System.err.println("Помилка читання або запису.");
            return 4;
        }
    }
}