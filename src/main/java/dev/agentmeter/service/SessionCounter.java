package dev.agentmeter.service;

import dev.agentmeter.model.AgentDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Conta arquivos de sessão modificados nas últimas 24 horas. A varredura é
 * reaproveitada por 30 segundos: percorrer as pastas de sessão a cada coleta
 * custava mais do que a informação muda.
 */
public final class SessionCounter {
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AgentRegistry registry;
    private Map<String, Integer> cached = Map.of();
    private Instant cachedAt;

    public SessionCounter(AgentRegistry registry) { this.registry = registry; }

    public Map<String, Integer> countRecent() {
        Instant now = Instant.now();
        if (cachedAt != null && cachedAt.isAfter(now.minus(CACHE_TTL))) return cached;
        Path home = Path.of(System.getProperty("user.home"));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (AgentDefinition agent : registry.all()) {
            if (agent.sessionsPath().isBlank()) continue;
            result.put(agent.id(), countRecentFiles(home.resolve(agent.sessionsPath())));
        }
        cached = Collections.unmodifiableMap(result);
        cachedAt = now;
        return cached;
    }

    private int countRecentFiles(Path root) {
        if (!Files.isDirectory(root)) return 0;
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        try (Stream<Path> files = Files.walk(root, 6)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .filter(path -> modifiedAfter(path, since))
                    .limit(10_000).count();
        } catch (IOException | SecurityException ignored) {
            return 0;
        }
    }

    private boolean modifiedAfter(Path path, Instant since) {
        try { return Files.getLastModifiedTime(path).toInstant().isAfter(since); }
        catch (IOException | SecurityException ignored) { return false; }
    }
}
