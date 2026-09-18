package dev.agentmeter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageMonitorTest {
    @Test void readsLinesFromNewestToOldest(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rollout.jsonl");
        Files.writeString(file, "primeira\nsegunda\nterceira\n", StandardCharsets.UTF_8);
        assertEquals(List.of("terceira", "segunda", "primeira"), UsageMonitor.tailLines(file));
    }

    @Test void keepsAccentedCharactersIntact(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("acentos.jsonl");
        Files.writeString(file, "{\"texto\":\"sessão de limitação\"}\n", StandardCharsets.UTF_8);
        assertTrue(UsageMonitor.tailLines(file).get(0).contains("sessão de limitação"));
    }

    @Test void ignoresBlankLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("vazio.jsonl");
        Files.writeString(file, "\n\numa\n\n", StandardCharsets.UTF_8);
        assertEquals(List.of("uma"), UsageMonitor.tailLines(file));
    }

    @Test void emptyFileYieldsNothing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nada.jsonl");
        Files.createFile(file);
        assertTrue(UsageMonitor.tailLines(file).isEmpty());
    }
}
