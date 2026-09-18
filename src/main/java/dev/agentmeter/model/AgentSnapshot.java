package dev.agentmeter.model;

import java.time.Instant;

/**
 * Medição de processos de um agente em um instante.
 *
 * @param cpuPercent      soma da carga dos processos, em "por cento de um núcleo";
 *                        pode passar de 100 em processos com várias threads
 * @param machinePercent  a mesma carga expressa como fração da capacidade total da máquina
 * @param warmingUp       verdadeiro enquanto não houver duas amostras para calcular carga
 */
public record AgentSnapshot(
        String agentId,
        boolean online,
        int processCount,
        double cpuPercent,
        double machinePercent,
        long memoryBytes,
        double memoryPercent,
        long uptimeMillis,
        boolean warmingUp,
        Instant capturedAt
) {
    public static AgentSnapshot offline(String agentId, Instant now) {
        return new AgentSnapshot(agentId, false, 0, 0, 0, 0, 0, 0, false, now);
    }
}
