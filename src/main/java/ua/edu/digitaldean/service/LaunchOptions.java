package ua.edu.digitaldean.service;

import java.nio.file.Path;
import java.util.Objects;

public record LaunchOptions(
        String mode,
        String sourceFormat,
        Path input,
        String targetFormat,
        Path output) {

    public LaunchOptions {
        Objects.requireNonNull(mode, "Режим не може бути null.");
        switch (mode) {
            case "--resource" -> {
                if (!"csv".equals(sourceFormat) || input != null || targetFormat != null || output != null) {
                    throw new IllegalArgumentException("Несумісні параметри для --resource.");
                }
            }
            case "--version" -> {
                if (sourceFormat != null || input != null || targetFormat != null || output != null) {
                    throw new IllegalArgumentException("Несумісні параметри для --version.");
                }
            }
            case "--seed" -> {
                if (!"csv".equals(sourceFormat) || input != null || !"json".equals(targetFormat) || output == null) {
                    throw new IllegalArgumentException("Несумісні параметри для --seed.");
                }
            }
            case "--inspect" -> {
                RequestFiles.requireFormat(sourceFormat);
                if (input == null || targetFormat != null || output != null) {
                    throw new IllegalArgumentException("Несумісні параметри для --inspect.");
                }
            }
            case "--convert" -> {
                RequestFiles.requireFormat(sourceFormat);
                RequestFiles.requireFormat(targetFormat);
                if (input == null || output == null) {
                    throw new IllegalArgumentException("Несумісні параметри для --convert.");
                }
            }
            default -> throw new IllegalArgumentException("Невідомий режим: " + mode);
        }
    }
}