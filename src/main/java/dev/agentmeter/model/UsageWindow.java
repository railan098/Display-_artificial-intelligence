package dev.agentmeter.model;

import java.time.Instant;

/**
 * Uma janela de limite publicada pelo agente.
 *
 * @param windowMinutes duração da janela; 0 quando o agente não informa.
 */
public record UsageWindow(String label, double usedPercent, Instant resetsAt, long windowMinutes) {
    public UsageWindow(String label, double usedPercent, Instant resetsAt) {
        this(label, usedPercent, resetsAt, 0);
    }

    public double remainingPercent() { return Math.max(0, Math.min(100, 100 - usedPercent)); }

    /**
     * Janela cujo horário de redefinição já passou: o percentual guardado
     * localmente descreve um ciclo encerrado e não vale mais.
     */
    public boolean expired() { return resetsAt != null && resetsAt.isBefore(Instant.now()); }
}
