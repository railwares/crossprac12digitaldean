package ua.edu.digitaldean.app;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ua.edu.digitaldean.service.LaunchOptions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandLineTest {
    @Test
    void parsesEmptyArgsAsResourceMode() {
        assertEquals(new LaunchOptions("--resource", "csv", null, null, null),
                CommandLine.parse(new String[0]));
    }

    @Test
    void parsesExplicitResourceMode() {
        assertEquals(new LaunchOptions("--resource", "csv", null, null, null),
                CommandLine.parse(new String[]{"--resource"}));
    }

    @Test
    void parsesSeedMode() {
        assertEquals(new LaunchOptions("--seed", "csv", null, "json", Path.of("out.json")),
                CommandLine.parse(new String[]{"--seed", "out.json"}));
    }

    @Test
    void parsesInspectCsvMode() {
        assertEquals(new LaunchOptions("--inspect", "csv", Path.of("in.csv"), null, null),
                CommandLine.parse(new String[]{"--inspect", "csv", "in.csv"}));
    }

    @Test
    void parsesInspectJsonMode() {
        assertEquals(new LaunchOptions("--inspect", "json", Path.of("in.json"), null, null),
                CommandLine.parse(new String[]{"--inspect", "json", "in.json"}));
    }

    @Test
    void parsesConvertCsvToJsonMode() {
        assertEquals(new LaunchOptions("--convert", "csv", Path.of("in.csv"), "json", Path.of("out.json")),
                CommandLine.parse(new String[]{"--convert", "csv", "in.csv", "json", "out.json"}));
    }

    @Test
    void parsesVersionMode() {
        assertEquals(new LaunchOptions("--version", null, null, null, null),
                CommandLine.parse(new String[]{"--version"}));
    }

    @Test
    void rejectsNullArrayAndNullElement() {
        assertThrows(NullPointerException.class, () -> CommandLine.parse(null));
        assertThrows(NullPointerException.class, () -> CommandLine.parse(new String[]{null}));
    }

    @Test
    void launchOptionsRejectsIncompatibleCombinations() {
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchOptions("--inspect", "json", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchOptions("--seed", "csv", Path.of("in.csv"), "json", Path.of("out.json")));
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchOptions("--resource", "json", null, null, null));
    }
}