package dev.agentmeter.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageSnapshotTest {
    @Test void acceptsFreshUsageWithFutureReset() {
        UsageSnapshot snapshot = snapshot(window(0, 4), Instant.now());
        assertFalse(snapshot.stale());
        assertEquals(1, snapshot.validWindows().size());
    }

    @Test void rejectsOldObservation() {
        assertTrue(snapshot(window(20, 24), Instant.now().minus(13, ChronoUnit.HOURS)).stale());
    }

    @Test void marksOnlyTheWindowThatAlreadyReset() {
        UsageSnapshot snapshot = new UsageSnapshot("claude",
                List.of(new UsageWindow("5 horas", 80, Instant.now().minus(1, ChronoUnit.MINUTES)),
                        new UsageWindow("Semanal", 30, Instant.now().plus(3, ChronoUnit.DAYS))),
                Instant.now(), "test");
        assertFalse(snapshot.stale());
        assertEquals(List.of("Semanal"), snapshot.validWindows().stream().map(UsageWindow::label).toList());
    }

    @Test void remainingPercentStaysInRange() {
        assertEquals(0d, new UsageWindow("x", 140, null).remainingPercent());
        assertEquals(100d, new UsageWindow("x", -20, null).remainingPercent());
    }

    @Test void claudeCacheIsOnlyFreshForOneMinute() {
        UsageSnapshot recent = new UsageSnapshot("claude",
                List.of(new UsageWindow("5 horas", 0, Instant.now().plus(3, ChronoUnit.HOURS), 300)),
                Instant.now().minus(30, ChronoUnit.SECONDS), "test");
        UsageSnapshot old = new UsageSnapshot("claude",
                List.of(new UsageWindow("5 horas", 0, Instant.now().plus(3, ChronoUnit.HOURS), 300)),
                Instant.now().minus(2, ChronoUnit.MINUTES), "test");
        assertFalse(recent.outdated());
        assertTrue(old.outdated());
        assertFalse(old.stale(), "o arquivo existe, mas a UI precisa ocultar os percentuais");
    }

    @Test void claudeWeeklyWindowUsesTheSameStrictCacheAge() {
        UsageSnapshot snapshot = new UsageSnapshot("claude",
                List.of(new UsageWindow("Semanal", 20, Instant.now().plus(3, ChronoUnit.DAYS), 10080)),
                Instant.now().minus(2, ChronoUnit.MINUTES), "test");
        assertTrue(snapshot.outdated());
    }

    @Test void codexFiveHourWindowKeepsFifteenMinuteTolerance() {
        UsageSnapshot snapshot = new UsageSnapshot("codex",
                List.of(new UsageWindow("5 horas", 20, Instant.now().plus(3, ChronoUnit.HOURS), 300)),
                Instant.now().minus(10, ChronoUnit.MINUTES), "test");
        assertFalse(snapshot.outdated());
    }

    @Test void unknownWindowFallsBackToFifteenMinutes() {
        UsageSnapshot snapshot = new UsageSnapshot("codex",
                List.of(new UsageWindow("Sem duração", 10, Instant.now().plus(2, ChronoUnit.HOURS))),
                Instant.now().minus(20, ChronoUnit.MINUTES), "test");
        assertTrue(snapshot.outdated());
    }

    private static UsageWindow window(double used, int hoursAhead) {
        return new UsageWindow("5 horas", used, Instant.now().plus(hoursAhead, ChronoUnit.HOURS));
    }

    private static UsageSnapshot snapshot(UsageWindow window, Instant observedAt) {
        return new UsageSnapshot("claude", List.of(window), observedAt, "test");
    }
}
