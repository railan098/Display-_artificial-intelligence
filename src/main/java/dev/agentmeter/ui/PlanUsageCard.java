package dev.agentmeter.ui;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.model.UsageWindow;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class PlanUsageCard extends VBox {
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("dd/MM · HH:mm").withZone(ZoneId.systemDefault());
    private static final Locale PT = Locale.forLanguageTag("pt-BR");

    private final AgentDefinition agent;
    private final Label state = new Label("AGUARDANDO");
    private final VBox limits = new VBox(14);
    private final Label source = new Label();

    public PlanUsageCard(AgentDefinition agent) {
        this.agent = agent;
        getStyleClass().addAll("surface", "usage-card");
        Label mark = new Label(agent.mark());
        mark.getStyleClass().add("usage-mark");
        mark.setStyle("-fx-background-color: " + agent.accent() + "; -fx-text-fill: #121512;");
        Label title = new Label(agent.label() + " · Limites do plano");
        title.getStyleClass().add("usage-title");
        Label detail = new Label("Janela compartilhada da conta");
        detail.getStyleClass().add("support-copy");
        VBox names = new VBox(2, title, detail);
        HBox.setHgrow(names, Priority.ALWAYS);
        state.getStyleClass().addAll("data-state", "waiting");
        state.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox head = new HBox(10, mark, names, state);
        head.setAlignment(Pos.CENTER_LEFT);
        source.getStyleClass().add("source-copy");
        getChildren().addAll(head, limits, source);
        update(UsageSnapshot.unavailable(agent.id(), "Aguardando leitura local"));
    }

    public void update(UsageSnapshot snapshot) {
        limits.getChildren().clear();
        state.getStyleClass().removeAll("fresh", "stale", "waiting");
        List<UsageWindow> valid = snapshot.validWindows();
        if (!snapshot.available()) {
            state.setText("INDISPONÍVEL");
            state.getStyleClass().add("waiting");
            limits.getChildren().add(note("Nenhum limite foi publicado localmente pelo agente ainda.", "empty-usage"));
        } else if (snapshot.stale() || snapshot.outdated() || valid.isEmpty()) {
            boolean needsClaudeRefresh = "claude".equals(agent.id()) && snapshot.outdated();
            state.setText(needsClaudeRefresh ? "ATUALIZE /USAGE" : "DADO EXPIRADO");
            state.getStyleClass().add("stale");
            limits.getChildren().add(note(needsClaudeRefresh
                    ? "Leitura de " + UiFormat.age(snapshot.age()) + " ocultada. Execute /usage no Claude Code; somente a nova consulta será mostrada."
                    : expiredMessage(), "expired-usage"));
        } else {
            // Uma janela que já virou não invalida a outra: mostramos a que
            // continua válida e sinalizamos apenas a que precisa de atualização.
            boolean complete = valid.size() == snapshot.windows().size();
            state.setText(complete ? ("claude".equals(agent.id()) ? "LEITURA AGORA" : "DADO REAL") : "PARCIAL");
            state.getStyleClass().add(!complete ? "stale" : "fresh");
            snapshot.windows().stream().limit(2).forEach(window -> limits.getChildren().add(
                    window.expired() ? note(window.label() + " · aguardando nova medição do agente.", "expired-usage")
                            : windowRow(window)));
        }
        String observed = snapshot.observedAt() == null ? "sem atualização"
                : "medido em " + UPDATED.format(snapshot.observedAt()) + " · " + UiFormat.age(snapshot.age());
        source.setText(snapshot.source() + " · " + observed);
    }

    private String expiredMessage() {
        return "claude".equals(agent.id())
                ? "Percentuais ocultados para evitar informação incorreta. Execute /usage no Claude Code para atualizar."
                : "Percentuais ocultados até o " + agent.label() + " publicar uma nova medição de uso.";
    }

    private Label note(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private VBox windowRow(UsageWindow window) {
        Label title = new Label(window.label()); title.getStyleClass().add("limit-title");
        Label reset = new Label(UiFormat.resetAt(window.resetsAt())); reset.getStyleClass().add("limit-reset");
        VBox left = new VBox(2, title, reset); HBox.setHgrow(left, Priority.ALWAYS);
        Label percent = new Label(String.format(PT, "%.0f%% restantes", window.remainingPercent()));
        percent.getStyleClass().add("limit-percent");
        HBox line = new HBox(left, percent); line.setAlignment(Pos.CENTER_LEFT);
        ProgressBar bar = new ProgressBar(window.remainingPercent() / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("usage-progress");
        return new VBox(7, line, bar);
    }
}
