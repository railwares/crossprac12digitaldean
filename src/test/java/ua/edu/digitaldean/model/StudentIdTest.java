package ua.edu.digitaldean.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentIdTest {
    @Test
    void normalizesCaseAndOuterWhitespace() {
        assertEquals("ST-2025-0042", new StudentId(" st-2025-0042 ").value());
    }

    @Test
    void recordEqualityUsesNormalizedValue() {
        assertEquals(new StudentId("ST-2025-0042"), new StudentId("st-2025-0042"));
    }

    @Test
    void rejectsEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> new StudentId(""));
    }

    @Test
    void rejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () -> new StudentId(null));
    }

    @Test
    void rejectsWrongShape() {
        assertThrows(IllegalArgumentException.class, () -> new StudentId("ST-25-42"));
    }
}