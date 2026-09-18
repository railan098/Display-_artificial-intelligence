package dev.agentmeter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentDefinition.MatchRule;
import dev.agentmeter.model.AgentDefinition.UsageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo de agentes monitorados. Traz um conjunto interno e carrega
 * definições extras de {@code ~/.agentmeter/agents.json}, recarregadas
 * automaticamente quando o arquivo muda.
 */
public final class AgentRegistry {
    public static final String CODEX = "codex";
    public static final String CLAUDE = "claude";

    private final ObjectMapper json = new ObjectMapper();
    private final Path pluginFile;
    private final AppSettings settings;

    private volatile Map<String, AgentDefinition> agents = Map.of();
    private volatile List<String> problems = List.of();
    private long pluginStamp = -1;

    public AgentRegistry(AppSettings settings) {
        this(settings, Path.of(System.getProperty("user.home"), ".agentmeter", "agents.json"));
    }

    AgentRegistry(AppSettings settings, Path pluginFile) {
        this.settings = settings;
        this.pluginFile = pluginFile;
        refresh();
    }

    public Path pluginFile() { return pluginFile; }

    /** Todos os agentes conhecidos, na ordem de exibição. */
    public List<AgentDefinition> all() { return List.copyOf(agents.values()); }

    /** Agentes destacados no painel principal e na caixa compacta. */
    public List<AgentDefinition> primary() { return agents.values().stream().filter(AgentDefinition::primary).toList(); }

    public AgentDefinition byId(String id) { return agents.get(id); }

    /** Mensagens de erro das definições externas, para exibição na interface. */
    public List<String> problems() { return problems; }

    /** Relê o arquivo de plugins quando ele muda; barato o bastante para cada coleta. */
    public synchronized boolean refresh() {
        long stamp = stampOf(pluginFile);
        boolean changed = stamp != pluginStamp || agents.isEmpty();
        if (!changed) return false;
        pluginStamp = stamp;

        Map<String, AgentDefinition> merged = new LinkedHashMap<>();
        for (AgentDefinition builtin : builtIns()) merged.put(builtin.id(), builtin);

        List<String> issues = new ArrayList<>();
        for (AgentDefinition extra : loadPlugins(issues)) {
            AgentDefinition existing = merged.get(extra.id());
            merged.put(extra.id(), existing == null ? extra : mergeIntoBuiltIn(existing, extra));
        }
        applyUserAliases(merged);
        agents = Collections.unmodifiableMap(merged);
        problems = List.copyOf(issues);
        return true;
    }

    /** Aliases digitados nas Configurações continuam valendo por cima das regras. */
    private void applyUserAliases(Map<String, AgentDefinition> merged) {
        if (settings == null) return;
        applyAlias(merged, CODEX, settings.codexAliases());
        applyAlias(merged, CLAUDE, settings.claudeAliases());
    }

    private void applyAlias(Map<String, AgentDefinition> merged, String id, String aliases) {
        AgentDefinition agent = merged.get(id);
        if (agent == null || aliases == null || aliases.isBlank()) return;
        List<String> extra = List.of(aliases.split(","));
        merged.put(id, new AgentDefinition(agent.id(), agent.label(), agent.provider(), agent.mark(), agent.accent(),
                agent.match().withExtraNames(extra), agent.usageSource(), agent.sessionsPath(), agent.primary(),
                agent.origin()));
    }

    /** Uma definição externa com o mesmo id ajusta o interno sem apagar o que não declarou. */
    private AgentDefinition mergeIntoBuiltIn(AgentDefinition builtin, AgentDefinition extra) {
        MatchRule base = builtin.match();
        MatchRule add = extra.match();
        MatchRule combined = new MatchRule(
                concat(base.names(), add.names()),
                concat(base.pathContains(), add.pathContains()),
                concat(base.commandContains(), add.commandContains()),
                concat(base.exclude(), add.exclude()));
        return new AgentDefinition(builtin.id(), extra.label(), extra.provider().isBlank() ? builtin.provider() : extra.provider(),
                extra.mark(), extra.accent(), combined,
                extra.usageSource() == UsageSource.NONE ? builtin.usageSource() : extra.usageSource(),
                extra.sessionsPath().isBlank() ? builtin.sessionsPath() : extra.sessionsPath(),
                extra.primary(), extra.origin());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>(a);
        for (String value : b) if (!result.contains(value)) result.add(value);
        return result;
    }

    private List<AgentDefinition> loadPlugins(List<String> issues) {
        if (!Files.isRegularFile(pluginFile)) return List.of();
        List<AgentDefinition> result = new ArrayList<>();
        try {
            JsonNode root = json.readTree(pluginFile.toFile());
            JsonNode list = root.isArray() ? root : root.path("agents");
            if (!list.isArray()) {
                issues.add("agents.json precisa ser uma lista ou conter a chave \"agents\".");
                return List.of();
            }
            for (JsonNode node : list) {
                AgentDefinition definition = parse(node, issues);
                if (definition != null) result.add(definition);
            }
        } catch (IOException | SecurityException error) {
            issues.add("Não foi possível ler " + pluginFile + ": " + error.getMessage());
        }
        return result;
    }

    private AgentDefinition parse(JsonNode node, List<String> issues) {
        String id = node.path("id").asText("").trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            issues.add("Definição ignorada: campo \"id\" ausente.");
            return null;
        }
        JsonNode match = node.path("match");
        MatchRule rule = new MatchRule(texts(match.path("names")), texts(match.path("pathContains")),
                texts(match.path("commandContains")), texts(match.path("exclude")));
        UsageSource source;
        try {
            String raw = node.path("usageSource").asText("NONE").trim().toUpperCase(Locale.ROOT);
            source = UsageSource.valueOf(raw);
        } catch (IllegalArgumentException error) {
            issues.add("Agente \"" + id + "\": usageSource desconhecido; use NONE, CODEX_ROLLOUT ou CLAUDE_CACHE.");
            source = UsageSource.NONE;
        }
        AgentDefinition definition = new AgentDefinition(id, node.path("label").asText(id),
                node.path("provider").asText(""), node.path("mark").asText(""), node.path("accent").asText(""),
                rule, source, node.path("sessionsPath").asText(""), node.path("primary").asBoolean(false),
                pluginFile.getFileName().toString());
        if (!definition.valid()) {
            issues.add("Agente \"" + id + "\": nenhuma regra de identificação informada em \"match\".");
            return null;
        }
        return definition;
    }

    private static List<String> texts(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) if (item.isTextual()) values.add(item.asText());
        return values;
    }

    private static long stampOf(Path path) {
        try { return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() + Files.size(path) : 0; }
        catch (IOException | SecurityException ignored) { return 0; }
    }

    private static List<AgentDefinition> builtIns() {
        return List.of(
                new AgentDefinition(CODEX, "Codex", "OpenAI", "C", "#e9eee8",
                        new MatchRule(List.of("codex", "codex.exe", "codex-code-mode-host"),
                                List.of("/codex/bin/codex", "\\codex\\bin\\codex"),
                                List.of("@openai/codex", "codex-code-mode-host"),
                                List.of("agent-meter", "agentmeter")),
                        UsageSource.CODEX_ROLLOUT, ".codex/sessions", true, "integrado"),
                new AgentDefinition(CLAUDE, "Claude Code", "Anthropic", "A", "#d9784b",
                        new MatchRule(List.of("claude", "claude.exe"),
                                List.of("/claude-code", "\\claude-code", "/native-binary/claude"),
                                List.of("@anthropic-ai/claude-code"),
                                List.of("agent-meter", "agentmeter", "shell-snapshots")),
                        UsageSource.CLAUDE_CACHE, ".claude/projects", true, "integrado"),
                new AgentDefinition("gemini", "Gemini CLI", "Google", "G", "#7aa2f7",
                        new MatchRule(List.of("gemini", "gemini.exe"), List.of(),
                                List.of("@google/gemini-cli"), List.of("agent-meter")),
                        UsageSource.NONE, ".gemini/tmp", false, "integrado"),
                new AgentDefinition("copilot", "GitHub Copilot CLI", "GitHub", "H", "#b6bfc9",
                        new MatchRule(List.of("copilot", "copilot.exe", "copilot-language-server"), List.of(),
                                List.of("@github/copilot"), List.of("agent-meter")),
                        UsageSource.NONE, "", false, "integrado"),
                new AgentDefinition("aider", "Aider", "Aider AI", "D", "#c0a36e",
                        new MatchRule(List.of("aider", "aider.exe"), List.of("/bin/aider", "\\scripts\\aider"),
                                List.of(), List.of("agent-meter")),
                        UsageSource.NONE, "", false, "integrado"),
                new AgentDefinition("cursor", "Cursor Agent", "Cursor", "U", "#9e8cf0",
                        new MatchRule(List.of("cursor-agent", "cursor-agent.exe"), List.of("/.cursor/bin/"),
                                List.of(), List.of("agent-meter")),
                        UsageSource.NONE, "", false, "integrado"),
                new AgentDefinition("ollama", "Ollama", "Ollama", "O", "#8fd694",
                        new MatchRule(List.of("ollama", "ollama.exe"), List.of(),
                                List.of("ollama serve", "ollama runner"), List.of("agent-meter")),
                        UsageSource.NONE, "", false, "integrado"));
    }
}
