package dev.agentmeter.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record UsageSnapshot(String agentId, List<UsageWindow> windows, Instant observedAt, String source) {
    /** Acima disso o número é escondido: velho demais para significar algo. */
    private static final Duration MAX_AGE = Duration.ofHours(12);
    private static final Duration DEFAULT_FRESH = Duration.ofMinutes(15);
    private static final Duration MIN_FRESH = Duration.ofMinutes(5);
    private static final Duration MAX_FRESH = Duration.ofHours(1);
    /**
     * O cache do Claude não acompanha o uso feito depois de /usage. Portanto,
     * ele só pode ser tratado como uma fotografia momentânea, nunca como um
     * contador ao vivo.
     */
    private static final Duration CLAUDE_FRESH = Duration.ofMinutes(1);

    public UsageSnapshot {
        windows = windows == null ? List.of() : List.copyOf(windows);
    }

    public boolean available() { return !windows.isEmpty(); }

    public Duration age() {
        return observedAt == null ? MAX_AGE.multipliedBy(2) : Duration.between(observedAt, Instant.now());
    }

    /**
     * Tolerância proporcional à menor janela: 5% da duração dela. Uma janela de
     * 5 horas aceita 15 minutos de atraso; uma semanal, bem mais. Sem isso, um
     * número de horas atrás aparecia como medição atual.
     */
    public Duration freshnessLimit() {
        if ("claude".equals(agentId)) return CLAUDE_FRESH;
        long shortest = windows.stream().mapToLong(UsageWindow::windowMinutes).filter(value -> value > 0).min().orElse(0);
        if (shortest <= 0) return DEFAULT_FRESH;
        Duration proportional = Duration.ofSeconds(Math.round(shortest * 60 * 0.05));
        if (proportional.compareTo(MIN_FRESH) < 0) return MIN_FRESH;
        return proportional.compareTo(MAX_FRESH) > 0 ? MAX_FRESH : proportional;
    }

    /** Velho demais para ser mostrado. */
    public boolean stale() { return observedAt == null || age().compareTo(MAX_AGE) > 0; }

    /** Ainda utilizável, mas já fora da janela de frescor: exibir com a idade. */
    public boolean outdated() { return !stale() && age().compareTo(freshnessLimit()) > 0; }

    /** Janelas ainda dentro do ciclo medido. */
    public List<UsageWindow> validWindows() {
        return windows.stream().filter(window -> !window.expired()).toList();
    }

    public static UsageSnapshot unavailable(String agentId, String source) {
        return new UsageSnapshot(agentId, List.of(), null, source);
    }
}
