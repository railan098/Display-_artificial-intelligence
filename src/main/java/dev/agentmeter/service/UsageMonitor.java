package dev.agentmeter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.model.UsageWindow;

import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Lê os limites de plano que cada agente grava localmente. Nada é estimado:
 * quando a fonte não existe ou está velha, a leitura vira "indisponível".
 */
public final class UsageMonitor {
    /** Os eventos de limite ficam no fim do arquivo; 1 MB cobre com folga. */
    private static final long MAX_TAIL_BYTES = 1024L * 1024;
    private static final int MAX_FILES_PER_SCAN = 5;
    private static final long CLAUDE_REFRESH_INTERVAL_MS = 50_000;
    private static final Duration CLAUDE_CACHE_TARGET_AGE = Duration.ofSeconds(45);
    private static final long CLAUDE_COMMAND_TIMEOUT_SECONDS = 12;

    private final ObjectMapper json = new ObjectMapper();
    private final Path home = Path.of(System.getProperty("user.home"));
    private final AgentRegistry registry;
    private final Map<Path, CachedRead> cache = new HashMap<>();
    private final AtomicBoolean claudeRefreshRunning = new AtomicBoolean();
    private volatile long lastClaudeRefreshAttempt;

    public UsageMonitor(AgentRegistry registry) { this.registry = registry; }

    public Map<String, UsageSnapshot> sample() {
        refreshClaudeUsageIfNeeded();
        Map<String, UsageSnapshot> result = new LinkedHashMap<>();
        for (AgentDefinition agent : registry.all()) {
            result.put(agent.id(), switch (agent.usageSource()) {
                case CODEX_ROLLOUT -> readCodex(agent);
                case CLAUDE_CACHE -> readClaude(agent);
                case NONE -> UsageSnapshot.unavailable(agent.id(), "Sem fonte local de limites");
            });
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * O próprio Claude Code oferece /usage em modo print. Ele consulta a conta
     * autenticada, não consome tokens e atualiza cachedUsageUtilization. A
     * execução é assíncrona para uma rede lenta nunca congelar a telemetria.
     */
    private void refreshClaudeUsageIfNeeded() {
        boolean hasClaude = registry.all().stream()
                .anyMatch(agent -> agent.usageSource() == AgentDefinition.UsageSource.CLAUDE_CACHE);
        if (!hasClaude || claudeCacheYoungerThan(CLAUDE_CACHE_TARGET_AGE)) return;
        long now = System.currentTimeMillis();
        if (now - lastClaudeRefreshAttempt < CLAUDE_REFRESH_INTERVAL_MS) return;
        if (!claudeRefreshRunning.compareAndSet(false, true)) return;
        lastClaudeRefreshAttempt = now;
        Thread refresh = new Thread(() -> {
            Process process = null;
            try {
                List<String> command = claudeUsageCommand();
                if (command.isEmpty()) return;
                process = new ProcessBuilder(command)
                        .directory(home.toFile())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (!process.waitFor(CLAUDE_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    System.err.println("Atualização /usage do Claude excedeu " + CLAUDE_COMMAND_TIMEOUT_SECONDS + "s.");
                } else if (process.exitValue() != 0) {
                    System.err.println("Atualização /usage do Claude terminou com código " + process.exitValue() + ".");
                }
            } catch (IOException error) {
                // Sem executável ou sem autenticação: a UI mantém o traço, nunca um número antigo.
                System.err.println("Não foi possível atualizar /usage do Claude: " + error.getMessage());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                if (process != null) process.destroyForcibly();
            } finally {
                claudeRefreshRunning.set(false);
            }
        }, "agent-meter-claude-usage");
        refresh.setDaemon(true);
        refresh.start();
    }

    private boolean claudeCacheYoungerThan(Duration maximumAge) {
        Path settings = home.resolve(".claude.json");
        if (!Files.isRegularFile(settings)) return false;
        try {
            long fetchedAt = json.readTree(settings.toFile()).path("cachedUsageUtilization").path("fetchedAtMs").asLong(0);
            return fetchedAt > 0 && Duration.between(Instant.ofEpochMilli(fetchedAt), Instant.now()).compareTo(maximumAge) < 0;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private List<String> claudeUsageCommand() {
        Path executable = findClaudeExecutable();
        if (executable == null) return List.of();
        List<String> arguments = List.of("-p", "/usage", "--output-format", "json", "--no-session-persistence");
        if (isWindows() && executable.getFileName().toString().toLowerCase().endsWith(".cmd")) {
            List<String> command = new ArrayList<>();
            command.add("cmd.exe"); command.add("/d"); command.add("/c"); command.add(executable.toString());
            command.addAll(arguments);
            return command;
        }
        List<String> command = new ArrayList<>();
        command.add(executable.toString()); command.addAll(arguments);
        return command;
    }

    private Path findClaudeExecutable() {
        List<Path> candidates = new ArrayList<>();
        String executableName = isWindows() ? "claude.exe" : "claude";
        candidates.add(home.resolve(".local/bin").resolve(executableName));
        candidates.add(home.resolve(".claude/local").resolve(executableName));
        String path = System.getenv("PATH");
        if (path != null) for (String directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!directory.isBlank()) {
                candidates.add(Path.of(directory).resolve(executableName));
                if (isWindows()) candidates.add(Path.of(directory).resolve("claude.cmd"));
            }
        }
        return candidates.stream().filter(Files::isRegularFile)
                .filter(candidate -> isWindows() || Files.isExecutable(candidate)).findFirst().orElse(null);
    }

    private boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private UsageSnapshot readCodex(AgentDefinition agent) {
        String source = "Sessões locais do Codex";
        Path sessions = home.resolve(agent.sessionsPath().isBlank() ? ".codex/sessions" : agent.sessionsPath());
        if (!Files.isDirectory(sessions)) return UsageSnapshot.unavailable(agent.id(), source);
        try (Stream<Path> files = Files.walk(sessions)) {
            List<Path> recent = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .limit(MAX_FILES_PER_SCAN).toList();
            for (Path file : recent) {
                UsageSnapshot snapshot = cached(file, agent.id(), () -> readCodexFile(agent.id(), file));
                if (snapshot.available()) return snapshot;
            }
        } catch (IOException | SecurityException ignored) { }
        return UsageSnapshot.unavailable(agent.id(), source);
    }

    /** Reaproveita a leitura enquanto o arquivo não mudar de tamanho ou data. */
    private UsageSnapshot cached(Path file, String agentId, java.util.function.Supplier<UsageSnapshot> reader) {
        long stamp = modified(file) + size(file);
        CachedRead hit = cache.get(file);
        if (hit != null && hit.stamp == stamp && hit.snapshot.agentId().equals(agentId)) return hit.snapshot;
        UsageSnapshot snapshot = reader.get();
        if (cache.size() > 64) cache.clear();
        cache.put(file, new CachedRead(stamp, snapshot));
        return snapshot;
    }

    private UsageSnapshot readCodexFile(String agentId, Path file) {
        for (String line : tailLines(file)) {
            if (!line.contains("\"rate_limits\"")) continue;
            try {
                JsonNode root = json.readTree(line);
                JsonNode limits = root.path("payload").path("rate_limits");
                if (!limits.isObject()) continue;
                List<UsageWindow> windows = new ArrayList<>();
                addCodexWindow(windows, limits.path("primary"), "Limite de 5 horas");
                addCodexWindow(windows, limits.path("secondary"), "Limite semanal");
                if (windows.isEmpty()) continue;
                Instant observed = parseInstant(root.path("timestamp").asText(null), modified(file));
                return new UsageSnapshot(agentId, windows, observed, "rate_limits do Codex");
            } catch (IOException ignored) { }
        }
        return UsageSnapshot.unavailable(agentId, "Sessões locais do Codex");
    }

    private void addCodexWindow(List<UsageWindow> windows, JsonNode node, String fallbackLabel) {
        if (!node.isObject() || !node.path("used_percent").isNumber()) return;
        long minutes = node.path("window_minutes").asLong();
        String label = minutes == 300 ? "Limite de 5 horas" : minutes == 10080 ? "Limite semanal" : fallbackLabel;
        JsonNode reset = node.path("resets_at");
        Instant resetsAt = reset.isNumber() && reset.asLong() > 0 ? Instant.ofEpochSecond(reset.asLong()) : null;
        windows.add(new UsageWindow(label, node.path("used_percent").asDouble(), resetsAt, minutes));
    }

    private UsageSnapshot readClaude(AgentDefinition agent) {
        Path settings = home.resolve(".claude.json");
        if (!Files.isRegularFile(settings)) return UsageSnapshot.unavailable(agent.id(), "Cache local do Claude Code");
        return cached(settings, agent.id(), () -> readClaudeFile(agent.id(), settings));
    }

    private UsageSnapshot readClaudeFile(String agentId, Path settings) {
        try {
            JsonNode root = json.readTree(settings.toFile());
            JsonNode cacheNode = root.path("cachedUsageUtilization");
            String activeAccount = root.path("oauthAccount").path("accountUuid").asText("");
            String cachedAccount = cacheNode.path("accountUuid").asText("");
            if (activeAccount.isBlank() || !activeAccount.equals(cachedAccount)) {
                return UsageSnapshot.unavailable(agentId, "Cache pertence a outra conta do Claude");
            }
            JsonNode utilization = cacheNode.path("utilization");
            List<UsageWindow> windows = new ArrayList<>();
            addClaudeWindow(windows, utilization.path("five_hour"), "Limite de 5 horas", 300);
            addClaudeWindow(windows, utilization.path("seven_day"), "Limite semanal", 10080);
            Instant observed = cacheNode.path("fetchedAtMs").isNumber()
                    ? Instant.ofEpochMilli(cacheNode.path("fetchedAtMs").asLong())
                    : instantOrNull(modified(settings));
            return new UsageSnapshot(agentId, windows, observed, "Cache oficial do Claude Code");
        } catch (IOException | SecurityException ignored) {
            return UsageSnapshot.unavailable(agentId, "Cache local do Claude Code");
        }
    }

    private void addClaudeWindow(List<UsageWindow> windows, JsonNode node, String label, long windowMinutes) {
        if (!node.isObject() || !node.path("utilization").isNumber()) return;
        windows.add(new UsageWindow(label, node.path("utilization").asDouble(),
                parseInstant(node.path("resets_at").asText(null), 0), windowMinutes));
    }

    /**
     * Últimas linhas do arquivo, da mais recente para a mais antiga, lidas em
     * blocos e decodificadas como UTF-8. A versão anterior percorria até 4 MB
     * byte a byte e convertia cada byte em char, o que gastava cerca de um
     * segundo por coleta e corrompia qualquer caractere acentuado.
     */
    static List<String> tailLines(Path file) {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            if (length == 0) return lines;
            long from = Math.max(0, length - MAX_TAIL_BYTES);
            byte[] buffer = new byte[(int) (length - from)];
            input.seek(from);
            input.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            String[] parts = text.split("\n", -1);
            // Com leitura parcial, a primeira linha pode estar cortada ao meio.
            int first = from > 0 ? 1 : 0;
            for (int index = parts.length - 1; index >= first; index--) {
                String line = parts[index].strip();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (IOException | SecurityException ignored) { }
        return lines;
    }

    private static Instant parseInstant(String value, long fallbackMillis) {
        if (value != null && !value.isBlank()) {
            try { return Instant.parse(value); }
            catch (DateTimeParseException ignored) {
                try { return java.time.OffsetDateTime.parse(value).toInstant(); }
                catch (DateTimeParseException alsoIgnored) { }
            }
        }
        return instantOrNull(fallbackMillis);
    }

    private static Instant instantOrNull(long millis) { return millis > 0 ? Instant.ofEpochMilli(millis) : null; }

    private long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException | SecurityException ignored) { return 0; }
    }

    private long size(Path path) {
        try { return Files.size(path); } catch (IOException | SecurityException ignored) { return 0; }
    }

    private record CachedRead(long stamp, UsageSnapshot snapshot) {}
}
