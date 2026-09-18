package dev.agentmeter.ui;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

public final class AgentCard extends VBox {
    private static final Locale PT = Locale.forLanguageTag("pt-BR");

    private final int logicalCores;
    private final Label status = new Label("FORA DE EXECUÇÃO");
    private final Label cpu = new Label("0,0%");
    private final Label memory = new Label("0 MB");
    private final Label runtime = new Label("—");
    private final Label process = new Label("Nenhum processo");
    private final ProgressBar cpuBar = new ProgressBar(0);

    public AgentCard(AgentDefinition agent, int logicalCores) {
        this.logicalCores = Math.max(1, logicalCores);
        getStyleClass().addAll("surface", "agent-card");
        setSpacing(20);

        Label mark = new Label(agent.mark());
        mark.getStyleClass().add("agent-mark");
        mark.setStyle("-fx-background-color: " + agent.accent() + "; -fx-text-fill: #121512;");
        Label title = new Label(agent.label());
        title.getStyleClass().add("agent-title");
        Label provider = new Label(agent.provider().toUpperCase(Locale.ROOT) + " · MONITOR LOCAL");
        provider.getStyleClass().add("eyebrow");
        VBox names = new VBox(2, title, provider);
        HBox.setHgrow(names, Priority.ALWAYS);
        status.getStyleClass().addAll("status-pill", "offline");
        HBox head = new HBox(12, mark, names, status);
        head.setAlignment(Pos.CENTER_LEFT);

        cpu.getStyleClass().add("metric-hero");
        Label cpuLabel = new Label("CPU DO AGENTE");
        cpuLabel.getStyleClass().add("metric-label");
        Tooltip.install(cpu, new Tooltip("Soma da carga dos processos do agente. 100% equivale a um núcleo ocupado; "
                + "esta máquina tem " + this.logicalCores + " núcleos lógicos."));
        VBox cpuBox = new VBox(4, cpuLabel, cpu);
        HBox.setHgrow(cpuBox, Priority.ALWAYS);
        HBox metrics = new HBox(28, cpuBox, metric("MEMÓRIA", memory), metric("TEMPO ATIVO", runtime));
        metrics.setAlignment(Pos.BOTTOM_LEFT);

        cpuBar.setMaxWidth(Double.MAX_VALUE);
        cpuBar.getStyleClass().add("agent-progress");
        Tooltip.install(cpuBar, new Tooltip("Fração da capacidade total do processador."));
        process.getStyleClass().add("support-copy");
        getChildren().addAll(head, metrics, cpuBar, process);
    }

    private VBox metric(String caption, Label value) {
        Label label = new Label(caption.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("metric-label");
        value.getStyleClass().add("metric-value");
        return new VBox(5, label, value);
    }

    public void update(AgentSnapshot snapshot) {
        status.setText(snapshot.online() ? "ATIVO" : "FORA DE EXECUÇÃO");
        status.getStyleClass().removeAll("online", "offline");
        status.getStyleClass().add(snapshot.online() ? "online" : "offline");
        // Na primeira amostra ainda não existe intervalo para medir carga.
        cpu.setText(snapshot.warmingUp() ? "medindo…" : String.format(PT, "%.1f%%", snapshot.cpuPercent()));
        memory.setText(UiFormat.bytes(snapshot.memoryBytes()));
        runtime.setText(snapshot.online() ? UiFormat.duration(snapshot.uptimeMillis()) : "—");
        cpuBar.setProgress(Math.min(1, snapshot.machinePercent() / 100.0));
        process.setText(snapshot.processCount() == 0 ? "Nenhum processo detectado"
                : snapshot.processCount() + (snapshot.processCount() == 1 ? " processo monitorado" : " processos monitorados")
                  + String.format(PT, " · %.1f%% da máquina", snapshot.machinePercent()));
    }
}
