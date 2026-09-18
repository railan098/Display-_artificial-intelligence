package dev.agentmeter.service;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import dev.agentmeter.model.SystemSnapshot;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProcessMonitor {
    /**
     * Processos genéricos que costumam citar o caminho de um agente sem serem o
     * agente. Só entram na medição quando o próprio nome do executável está na
     * regra, nunca por conterem um texto no comando.
     */
    private static final Set<String> GENERIC_HOSTS = Set.of(
            "bash", "sh", "zsh", "fish", "dash", "ksh", "cmd.exe", "powershell.exe", "pwsh",
            "grep", "rg", "find", "cat", "tail", "head", "less", "awk", "sed", "xargs",
            "java", "javaw", "git", "docker", "tmux", "screen", "ssh");

    private final AgentRegistry registry;
    private final OperatingSystem os;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final int logicalCores;
    private final long ownPid = ProcessHandle.current().pid();
    private final Map<Integer, OSProcess> previousProcesses = new HashMap<>();
    private long[] previousCpuTicks;
    private boolean firstSample = true;

    public ProcessMonitor(AgentRegistry registry) {
        this.registry = registry;
        SystemInfo systemInfo = new SystemInfo();
        os = systemInfo.getOperatingSystem();
        processor = systemInfo.getHardware().getProcessor();
        memory = systemInfo.getHardware().getMemory();
        logicalCores = Math.max(1, processor.getLogicalProcessorCount());
        previousCpuTicks = processor.getSystemCpuLoadTicks();
    }

    public int logicalCores() { return logicalCores; }

    public synchronized Reading sample() {
        Instant now = Instant.now();
        List<OSProcess> processes = os.getProcesses();
        List<AgentDefinition> definitions = registry.all();

        Map<Integer, OSProcess> matchedNow = new HashMap<>();
        Map<String, AgentSnapshot> agents = new LinkedHashMap<>();
        long totalMemory = memory.getTotal();

        for (AgentDefinition agent : definitions) {
            int count = 0;
            double cpu = 0;
            long rss = 0;
            long oldestStart = Long.MAX_VALUE;
            for (OSProcess process : processes) {
                if (!matches(agent, process)) continue;
                count++;
                matchedNow.put(process.getProcessID(), process);
                rss += Math.max(0, process.getResidentSetSize());
                if (process.getStartTime() > 0) oldestStart = Math.min(oldestStart, process.getStartTime());
                OSProcess previous = previousProcesses.get(process.getProcessID());
                // Sem amostra anterior não há carga instantânea: a média de vida
                // inteira do processo seria lida como uso "agora" e mentiria.
                if (previous == null) continue;
                double load = process.getProcessCpuLoadBetweenTicks(previous);
                if (Double.isFinite(load) && load > 0) cpu += load * 100.0;
            }
            double aggregate = Math.max(0, Math.min(logicalCores * 100.0, cpu));
            double memPercent = totalMemory == 0 ? 0 : rss * 100.0 / totalMemory;
            long uptime = count == 0 || oldestStart == Long.MAX_VALUE
                    ? 0 : Math.max(0, System.currentTimeMillis() - oldestStart);
            agents.put(agent.id(), new AgentSnapshot(agent.id(), count > 0, count, aggregate,
                    aggregate / logicalCores, rss, Math.max(0, Math.min(100, memPercent)), uptime,
                    firstSample && count > 0, now));
        }

        double systemCpu = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = processor.getSystemCpuLoadTicks();
        previousProcesses.clear();
        previousProcesses.putAll(matchedNow);
        firstSample = false;

        SystemSnapshot system = new SystemSnapshot(Math.max(0, Math.min(100, systemCpu)),
                Math.max(0, totalMemory - memory.getAvailable()), totalMemory, now);
        return new Reading(Collections.unmodifiableMap(agents), system);
    }

    /**
     * Identificação por campo separado: nome do executável, caminho e linha de
     * comando. Comparar um texto único concatenado fazia qualquer processo que
     * apenas citasse a pasta do agente ser contado como o agente.
     */
    boolean matches(AgentDefinition agent, OSProcess process) {
        if (process.getProcessID() == ownPid) return false;
        String name = safe(process.getName()).toLowerCase(Locale.ROOT);
        String path = safe(process.getPath()).toLowerCase(Locale.ROOT);
        String command = safe(process.getCommandLine()).toLowerCase(Locale.ROOT);

        AgentDefinition.MatchRule rule = agent.match();
        for (String token : rule.exclude()) {
            if (name.contains(token) || path.contains(token) || command.contains(token)) return false;
        }
        if (rule.names().contains(name) || rule.names().contains(baseName(path))) return true;
        if (GENERIC_HOSTS.contains(name)) return false;
        for (String token : rule.pathContains()) if (path.contains(token)) return true;
        for (String token : rule.commandContains()) if (command.contains(token)) return true;
        return false;
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public record Reading(Map<String, AgentSnapshot> agents, SystemSnapshot system) {
        public AgentSnapshot agent(String id) {
            AgentSnapshot snapshot = agents.get(id);
            return snapshot == null ? AgentSnapshot.offline(id, Instant.now()) : snapshot;
        }
    }
}
