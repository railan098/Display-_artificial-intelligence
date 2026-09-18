package dev.agentmeter.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UiFormatTest {
    @Test void formatsMemoryInMegabytes() { assertEquals("256 MB", UiFormat.bytes(256L * 1024 * 1024)); }
    @Test void formatsHoursAndMinutes() { assertEquals("2h 5min", UiFormat.duration((2 * 60 + 5) * 60_000L)); }
    @Test void neverShowsNegativeMemory() { assertEquals("0 KB", UiFormat.bytes(-10)); }
}
