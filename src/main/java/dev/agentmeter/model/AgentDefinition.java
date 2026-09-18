package dev.agentmeter.model;

import java.util.List;
import java.util.Locale;

/**
 * Descrição de um agente monitorável. As definições internas ficam no
 * {@code AgentRegistry}; definições adicionais podem ser carregadas de
 * {@code ~/.agentmeter/agents.json} sem recompilar o aplicativo.
 */
public record AgentDefinition(
        String id,
        String label,
        String provider,
        String mark,
        String accent,
        MatchRule match,
        UsageSource usageSource,
        String sessionsPath,
        boolean primary,
        String origin
) {
    public AgentDefinition {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        label = label == null || label.isBlank() ? id : label.trim();
        provider = provider == null ? "" : provider.trim();
        mark = mark == null || mark.isBlank() ? initial(label) : mark.trim();
        accent = accent == null || accent.isBlank() ? "#9aa79a" : accent.trim();
        match = match == null ? MatchRule.empty() : match;
        usageSource = usageSource == null ? UsageSource.NONE : usageSource;
        sessionsPath = sessionsPath == null ? "" : sessionsPath.trim();
        origin = origin == null ? "integrado" : origin;
    }

    public boolean valid() { return !id.isBlank() && match.usable(); }

    private static String initial(String value) {
        return value == null || value.isBlank() ? "?" : value.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /** Origem dos percentuais de limite do plano publicados localmente pelo agente. */
    public enum UsageSource { NONE, CODEX_ROLLOUT, CLAUDE_CACHE }

    /**
     * Regras de identificação de processo. O casamento é feito sobre campos
     * separados — nome do executável, caminho e linha de comando — e nunca sobre
     * um texto único concatenado, para evitar falsos positivos.
     */
    public record MatchRule(
            List<String> names,
            List<String> pathContains,
            List<String> commandContains,
            List<String> exclude
    ) {
        public MatchRule {
            names = lower(names);
            pathContains = lower(pathContains);
            commandContains = lower(commandContains);
            exclude = lower(exclude);
        }

        public static MatchRule empty() { return new MatchRule(List.of(), List.of(), List.of(), List.of()); }

        public boolean usable() { return !names.isEmpty() || !pathContains.isEmpty() || !commandContains.isEmpty(); }

        public MatchRule withExtraNames(List<String> extra) {
            if (extra == null || extra.isEmpty()) return this;
            List<String> merged = new java.util.ArrayList<>(commandContains);
            for (String value : extra) {
                String candidate = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (candidate.length() >= 3 && !merged.contains(candidate)) merged.add(candidate);
            }
            return new MatchRule(names, pathContains, merged, exclude);
        }

        private static List<String> lower(List<String> values) {
            if (values == null) return List.of();
            return values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
    }
}
