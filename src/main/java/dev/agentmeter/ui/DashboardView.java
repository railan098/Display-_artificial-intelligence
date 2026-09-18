package dev.agentmeter.ui;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import dev.agentmeter.model.SystemSnapshot;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.service.ProcessMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardView extends ScrollPane {
    private final Map<String, AgentCard> cards = new LinkedHashMap<>();
    private final Map<String, PlanUsageCard> usageCards = new LinkedHashMap<>();
    private final Map<String, XYChart.Series<Number, Number>> series = new LinkedHashMap<>();
    private final Label systemCpu = new Label("0%");
    private final Label systemMemory = new Label("0 GB");
    private final Label processTotal = new Label("0");
    private final Label sessions = new Label("0");
    private final Label detected = new Label("0");
    private final Label lastUpdate = new Label("Aguardando primeira leitura");
    private final Label health = new Label();
    private int tick;

    public DashboardView(List<AgentDefinition> primaryAgents, int logicalCores) {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("page-scroll");
        VBox content = new VBox(24);
        content.getStyleClass().add("page");
        content.setPadding(new Insets(34, 38, 42, 38));

        Label eyebrow = new Label("TELEMETRIA LOCAL · TEMPO REAL");
        eyebrow.getStyleClass().add("eyebrow-accent");
        Label title = new Label("Visão geral");
        title.getStyleClass().add("page-title");
        Label description = new Label("Uso real de recursos dos agentes de IA em execução neste computador.");
        description.getStyleClass().add("page-subtitle");
        Label liveDot = new Label("●  COLETA ATIVA");
        liveDot.getStyleClass().add("live-badge");
        HBox header = new HBox(new VBox(5, eyebrow, title, description), spacer(), liveDot);
        header.setAlignment(Pos.CENTER_LEFT);

        List<Node> agentNodes = new ArrayList<>();
        List<Node> usageNodes = new ArrayList<>();
        for (AgentDefinition agent : primaryAgents) {
            AgentCard card = new AgentCard(agent, logicalCores);
            cards.put(agent.id(), card);
            agentNodes.add(card);
            if (agent.usageSource() != AgentDefinition.UsageSource.NONE) {
                PlanUsageCard usage = new PlanUsageCard(agent);
                usageCards.put(agent.id(), usage);
                usageNodes.add(usage);
            }
            XYChart.Series<Number, Number> line = new XYChart.Series<>();
            line.setName(agent.label());
            series.put(agent.id(), line);
        }
        GridPane agentGrid = grid(agentNodes);
        GridPane usageGrid = grid(usageNodes);

        Label usageTitle = new Label("Limites reais do plano");
        usageTitle.getStyleClass().add("section-title");
        Label usageCopy = new Label("Percentuais restantes e redefinições publicados localmente por cada agente.");
        usageCopy.getStyleClass().add("support-copy");
        VBox usageSection = new VBox(10, new VBox(3, usageTitle, usageCopy), usageGrid);

        HBox quickMetrics = new HBox(12,
                quickCard("CPU DO SISTEMA", systemCpu, logicalCores + " núcleos lógicos"),
                quickCard("RAM EM USO", systemMemory, "memória total"),
                quickCard("PROCESSOS IA", processTotal, "todos os agentes"),
                quickCard("AGENTES ATIVOS", detected, "detectados agora"),
                quickCard("SESSÕES 24H", sessions, "arquivos locais"));
        for (Node node : quickMetrics.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 25);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        yAxis.setLabel("% da máquina");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setMinHeight(260);
        chart.setPrefHeight(290);
        chart.getStyleClass().add("telemetry-chart");
        chart.getData().addAll(series.values());
        Label chartTitle = new Label("Histórico de atividade");
        chartTitle.getStyleClass().add("section-title");
        Label chartCopy = new Label("CPU por agente como fração da máquina · últimas 60 amostras");
        chartCopy.getStyleClass().add("support-copy");
        VBox chartPanel = new VBox(12, new HBox(12, new VBox(3, chartTitle, chartCopy), spacer()), chart);
        chartPanel.getStyleClass().addAll("surface", "chart-panel");

        Label truth = new Label("DADOS VERIFICÁVEIS");
        truth.getStyleClass().add("eyebrow-accent");
        Label truthTitle = new Label("O que esta versão mede");
        truthTitle.getStyleClass().add("section-title");
        Label truthCopy = new Label("CPU, memória e processos vêm do sistema operacional. Os limites do plano vêm dos eventos de uso gravados pelos próprios agentes. Quando uma fonte está ausente ou antiga, isso é indicado claramente.");
        truthCopy.setWrapText(true);
        truthCopy.getStyleClass().add("support-copy");
        VBox truthPanel = new VBox(7, truth, truthTitle, truthCopy);
        truthPanel.getStyleClass().addAll("surface", "truth-panel");

        lastUpdate.getStyleClass().add("footer-copy");
        health.getStyleClass().add("expired-usage");
        health.setWrapText(true);
        health.setManaged(false);
        health.setVisible(false);
        content.getChildren().addAll(header, agentGrid, usageSection, quickMetrics, chartPanel, truthPanel, health, lastUpdate);
        setContent(content);

        widthProperty().addListener((obs, old, width) -> {
            int columns = width.doubleValue() < 920 ? 1 : 2;
            reflow(agentGrid, columns);
            reflow(usageGrid, columns);
        });
    }

    private GridPane grid(List<Node> nodes) {
        GridPane pane = new GridPane();
        pane.setHgap(18);
        pane.setVgap(16);
        for (Node node : nodes) {
            ((Region) node).setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(node, Priority.ALWAYS);
        }
        pane.getProperties().put("cards", nodes);
        reflow(pane, 2);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private void reflow(GridPane pane, int columns) {
        List<Node> nodes = (List<Node>) pane.getProperties().get("cards");
        if (nodes == null || nodes.isEmpty()) return;
        Integer applied = (Integer) pane.getProperties().get("columns");
        if (applied != null && applied == columns) return;
        pane.getProperties().put("columns", columns);
        pane.getChildren().clear();
        // Colunas de largura igual: sem isso um cartão com texto mais longo
        // roubava espaço do outro e truncava títulos.
        pane.getColumnConstraints().clear();
        for (int column = 0; column < columns; column++) {
            javafx.scene.layout.ColumnConstraints constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            pane.getColumnConstraints().add(constraint);
        }
        for (int index = 0; index < nodes.size(); index++) {
            pane.add(nodes.get(index), index % columns, index / columns);
        }
    }

    private VBox quickCard(String caption, Label value, String detail) {
        Label label = new Label(caption);
        label.getStyleClass().add("metric-label");
        value.getStyleClass().add("quick-value");
        Label small = new Label(detail);
        small.getStyleClass().add("support-copy");
        VBox box = new VBox(5, label, value, small);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().addAll("surface", "quick-card");
        return box;
    }

    private Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }

    public void update(ProcessMonitor.Reading reading, Map<String, Integer> sessionCounts,
                       Map<String, UsageSnapshot> usage, String collectionError) {
        cards.forEach((id, card) -> card.update(reading.agent(id)));
        usageCards.forEach((id, card) -> {
            UsageSnapshot snapshot = usage.get(id);
            if (snapshot != null) card.update(snapshot);
        });
        SystemSnapshot system = reading.system();
        systemCpu.setText(String.format("%.0f%%", system.cpuPercent()));
        systemMemory.setText(UiFormat.bytes(system.usedMemoryBytes()));
        processTotal.setText(Integer.toString(
                reading.agents().values().stream().mapToInt(AgentSnapshot::processCount).sum()));
        detected.setText(Long.toString(reading.agents().values().stream().filter(AgentSnapshot::online).count()));
        sessions.setText(Integer.toString(sessionCounts.values().stream().mapToInt(Integer::intValue).sum()));
        lastUpdate.setText("Última leitura às " + UiFormat.clock(system.capturedAt())
                + " · dados permanecem somente neste dispositivo");
        series.forEach((id, line) -> addPoint(line, reading.agent(id).machinePercent()));
        tick++;

        showCollectionFailure(collectionError);
    }

    /** Falha de coleta fica visível: dado antigo nunca é apresentado como atual. */
    public void showCollectionFailure(String message) {
        boolean failing = message != null && !message.isBlank();
        health.setText(failing
                ? "A última coleta falhou: " + message + " — os números acima são da leitura anterior."
                : "");
        health.setVisible(failing);
        health.setManaged(failing);
    }

    private void addPoint(XYChart.Series<Number, Number> line, double value) {
        line.getData().add(new XYChart.Data<>(tick, value));
        if (line.getData().size() > 60) line.getData().remove(0);
    }
}
