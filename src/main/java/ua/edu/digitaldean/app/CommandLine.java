package ua.edu.digitaldean.app;

import java.nio.file.Path;
import java.util.Objects;
import ua.edu.digitaldean.service.LaunchOptions;
import ua.edu.digitaldean.service.RequestFiles;

public final class CommandLine {
    private CommandLine() {
    }

    public static LaunchOptions parse(String[] args) {
        Objects.requireNonNull(args, "Масив аргументів не може бути null.");
        for (String argument : args) {
            Objects.requireNonNull(argument, "Аргумент не може бути null.");
        }

        if (args.length == 0 || (args.length == 1 && args[0].equals("--resource"))) {
            return new LaunchOptions("--resource", "csv", null, null, null);
        }
        if (args.length == 1 && args[0].equals("--version")) {
            return new LaunchOptions("--version", null, null, null, null);
        }
        if (args.length == 2 && args[0].equals("--seed")) {
            return new LaunchOptions("--seed", "csv", null, "json", path(args[1]));
        }
        if (args.length == 3 && args[0].equals("--inspect")) {
            RequestFiles.requireFormat(args[1]);
            return new LaunchOptions("--inspect", args[1], path(args[2]), null, null);
        }
        if (args.length == 5 && args[0].equals("--convert")) {
            RequestFiles.requireFormat(args[1]);
            RequestFiles.requireFormat(args[3]);
            return new LaunchOptions("--convert", args[1], path(args[2]), args[3], path(args[4]));
        }
        throw new IllegalArgumentException("Формат аргументів.");
    }

    private static Path path(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Шлях порожній.");
        }
        return Path.of(value);
    }
}