package dev.agentmeter.ui;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import dev.agentmeter.service.AgentRegistry;
import dev.agentmeter.service.ProcessMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo de agentes e plugins: mostra quem está sendo procurado, com quais
 * regras, de onde a regra veio e o que foi encontrado agora.
 */
public final class PluginsView extends ScrollPane {
    private static final String EXAMPLE = """
            {
              "agents": [
                {
                  "id": "meu-agente",
                  "label": "Meu Agente",
                  "provider": "Equipe interna",
                  "mark": "M",
                  "accent": "#8fd694",
                  "primary": false,
                  "usageSource": "NONE",
                  "sessionsPath": "",
                  "match": {
                    "names": ["meu-agente"],
                    "pathContains": ["/opt/meu-agente/"],
                    "commandContains": ["@minha-empresa/meu-agente"],
                    "exclude": ["agent-meter"]
                  }
                }
              ]
            }
            """;

    private final AgentRegistry registry;
    private final VBox catalog = new VBox(12);
    private final VBox problems = new VBox(6);
    private final Label fileState = new Label();
    private Map<String, AgentSnapshot> lastReading = Map.of();
    private List<AgentDefinition> rendered = List.of();

    public PluginsView(AgentRegistry registry) {
        this.registry = registry;
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("page-scroll");
        VBox content = new VBox(24);
        content.getStyleClass().add("page");
        content.setPadding(new Insets(34, 38, 42, 38));

        Label eyebrow = new Label("EXTENSIBILIDADE");
        eyebrow.getStyleClass().add("eyebrow-accent");
        Label title = new Label("Agentes e plugins");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Todo agente monitorado é uma definição. As internas vêm com o aplicativo; as suas ficam em um arquivo JSON e valem sem recompilar nada.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("page-subtitle");

        Button reload = new Button("Recarregar plugins");
        reload.getStyleClass().add("primary-button");
        reload.setOnAction(event -> { registry.refresh(); refresh(); });
        Button create = new Button("Criar arquivo de exemplo");
        create.setOnAction(event -> createExample());
        fileState.getStyleClass().add("support-copy");
        fileState.setWrapText(true);
        HBox actions = new HBox(12, reload, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox filePanel = new VBox(10,
                sectionTitle("ARQUIVO DE PLUGINS", "Definições do usuário"),
                fileState, actions);
        filePanel.getStyleClass().addAll("surface", "settings-section");

        problems.getStyleClass().add("plugin-problems");
        VBox catalogPanel = new VBox(14, sectionTitle("CATÁLOGO", "Agentes conhecidos"), problems, catalog);
        catalogPanel.getStyleClass().addAll("surface", "settings-section");

        content.getChildren().addAll(new VBox(5, eyebrow, title, subtitle), filePanel, catalogPanel);
        setContent(content);
        refresh();
    }

    /** Atualiza o estado de detecção mostrado ao lado de cada agente. */
    public void update(ProcessMonitor.Reading reading) {
        lastReading = reading.agents();
        if (!rendered.equals(registry.all())) refresh();
        else renderStatuses();
    }

    private void refresh() {
        rendered = registry.all();
        Path file = registry.pluginFile();
        fileState.setText(Files.isRegularFile(file)
                ? "Lendo " + file + " · recarregado automaticamente quando o arquivo muda."
                : "Nenhum arquivo em " + file + ". Crie um para adicionar agentes próprios.");
        problems.getChildren().clear();
        for (String problem : registry.problems()) {
            Label label = new Label("⚠  " + problem);
            label.setWrapText(true);
            label.getStyleClass().add("expired-usage");
            problems.getChildren().add(label);
        }
        problems.setManaged(!problems.getChildren().isEmpty());
        problems.setVisible(!problems.getChildren().isEmpty());
        catalog.getChildren().clear();
        for (AgentDefinition agent : rendered) catalog.getChildren().add(row(agent));
        renderStatuses();
    }

    private void renderStatuses() {
        for (var node : catalog.getChildren()) {
            String id = (String) node.getProperties().get("agentId");
            Label status = (Label) node.getProperties().get("status");
            if (id == null || status == null) continue;
            AgentSnapshot snapshot = lastReading.get(id);
            boolean online = snapshot != null && snapshot.online();
            status.setText(online ? snapshot.processCount() + (snapshot.processCount() == 1 ? " processo" : " processos")
                    : "não encontrado");
            status.getStyleClass().removeAll("online", "offline");
            status.getStyleClass().add(online ? "online" : "offline");
        }
    }

    private HBox row(AgentDefinition agent) {
        Label mark = new Label(agent.mark());
        mark.getStyleClass().add("mini-mark");
        mark.setStyle("-fx-background-color: " + agent.accent() + "; -fx-text-fill: #121512;");
        Label name = new Label(agent.label());
        name.getStyleClass().add("row-title");
        Label origin = new Label(agent.provider() + " · " + agent.origin()
                + (agent.primary() ? " · no painel" : "")
                + (agent.usageSource() == AgentDefinition.UsageSource.NONE ? "" : " · limites do plano"));
        origin.getStyleClass().add("support-copy");
        Label rules = new Label(describe(agent));
        rules.setWrapText(true);
        rules.getStyleClass().add("source-copy");
        VBox text = new VBox(3, name, origin, rules);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label status = new Label("não encontrado");
        status.getStyleClass().addAll("status-pill", "offline");
        // O texto das regras pode ser longo; o selo de estado não pode encolher.
        status.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(14, mark, text, status);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");
        row.getProperties().put("agentId", agent.id());
        row.getProperties().put("status", status);
        return row;
    }

    private String describe(AgentDefinition agent) {
        AgentDefinition.MatchRule rule = agent.match();
        StringBuilder text = new StringBuilder();
        append(text, "nome", rule.names());
        append(text, "caminho", rule.pathContains());
        append(text, "comando", rule.commandContains());
        append(text, "ignora", rule.exclude());
        return text.isEmpty() ? "sem regras" : text.toString();
    }

    private void append(StringBuilder text, String caption, List<String> values) {
        if (values.isEmpty()) return;
        if (!text.isEmpty()) text.append("   ·   ");
        text.append(caption).append(": ").append(String.join(", ", values));
    }

    private void createExample() {
        Path file = registry.pluginFile();
        try {
            if (Files.isRegularFile(file)) {
                fileState.setText("O arquivo " + file + " já existe; edite-o em vez de sobrescrever.");
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, EXAMPLE, StandardCharsets.UTF_8);
            registry.refresh();
            refresh();
            fileState.setText("Exemplo criado em " + file + ". Edite e use \"Recarregar plugins\".");
        } catch (IOException | SecurityException error) {
            fileState.setText("Não foi possível criar " + file + ": " + error.getMessage());
        }
    }

    private VBox sectionTitle(String eyebrowText, String titleText) {
        Label eyebrow = new Label(eyebrowText.toUpperCase(Locale.ROOT));
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        VBox box = new VBox(3, eyebrow, title);
        VBox.setMargin(title, new Insets(0, 0, 8, 0));
        return box;
    }
}
