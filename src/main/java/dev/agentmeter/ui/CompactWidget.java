package dev.agentmeter.ui;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.model.UsageWindow;
import dev.agentmeter.service.ProcessMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CompactWidget {
    private final Stage stage = new Stage(StageStyle.UNDECORATED);
    private final VBox root;
    private final Map<String, MiniAgent> agents = new LinkedHashMap<>();
    private double dragX;
    private double dragY;

    public CompactWidget(String stylesheet, List<AgentDefinition> primaryAgents, Runnable expand, Runnable quit) {
        Label mark = new Label("AM"); mark.getStyleClass().add("widget-mark");
        Label title = new Label("AGENT METER"); title.getStyleClass().add("widget-title");
        Label live = new Label("● AO VIVO"); live.getStyleClass().add("widget-live");
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Button expandButton = new Button("↗");
        expandButton.setAccessibleText("Expandir painel");
        expandButton.setTooltip(new Tooltip("Expandir painel"));
        expandButton.getStyleClass().add("widget-button");
        expandButton.setOnAction(event -> expand.run());
        Button close = new Button("×");
        close.setAccessibleText("Ocultar caixa");
        close.setTooltip(new Tooltip("Ocultar a caixa; a coleta continua"));
        close.getStyleClass().add("widget-button");
        close.setOnAction(event -> stage.hide());
        HBox header = new HBox(9, mark, title, live, grow, expandButton, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("widget-header");

        VBox body = new VBox(11);
        for (AgentDefinition agent : primaryAgents) {
            MiniAgent mini = new MiniAgent(agent);
            agents.put(agent.id(), mini);
            body.getChildren().add(mini.root);
        }
        Label hint = new Label("ARRASTE PARA MOVER  ·  ↗ EXPANDIR");
        hint.getStyleClass().add("widget-hint");
        root = new VBox(12, header, body, hint);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("widget-root");
        root.setOnMousePressed(this::startDrag);
        root.setOnMouseDragged(this::drag);
        Scene scene = new Scene(root, 370, 95 + Math.max(1, primaryAgents.size()) * 76);
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setTitle("Agent Meter compacto");
        stage.setOnCloseRequest(event -> { event.consume(); stage.hide(); });
    }

    public void update(ProcessMonitor.Reading reading, Map<String, UsageSnapshot> usage) {
        agents.forEach((id, mini) -> mini.update(reading.agent(id), usage.get(id)));
    }

    public void show() { stage.show(); stage.toFront(); }
    public void hide() { stage.hide(); }
    public boolean isShowing() { return stage.isShowing(); }
    public void setAlwaysOnTop(boolean value) { stage.setAlwaysOnTop(value); }

    public void snapshot(String target, Runnable finished) {
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        delay.setOnFinished(event -> {
            WritableImage image = root.snapshot(null, null);
            BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            try { ImageIO.write(output, "png", new File(target)); }
            catch (IOException error) { System.err.println("Falha ao capturar caixa compacta: " + error.getMessage()); }
            finished.run();
        });
        delay.play();
    }

    private void startDrag(MouseEvent event) { dragX = event.getSceneX(); dragY = event.getSceneY(); }
    private void drag(MouseEvent event) { stage.setX(event.getScreenX() - dragX); stage.setY(event.getScreenY() - dragY); }

    private static final class MiniAgent {
        private static final Locale PT = Locale.forLanguageTag("pt-BR");
        final HBox root;
        final Label status = new Label();
        final Label primary = new Label("—");
        final Label secondary = new Label("—");
        final ProgressBar primaryBar = new ProgressBar(0);
        final ProgressBar secondaryBar = new ProgressBar(0);
        final Tooltip ageHint = new Tooltip();
        final String agentLabel;
        String suffix = "";

        MiniAgent(AgentDefinition agent) {
            agentLabel = agent.label();
            Label mark = new Label(agent.mark());
            mark.getStyleClass().add("mini-mark");
            mark.setStyle("-fx-background-color: " + agent.accent() + "; -fx-text-fill: #121512;");
            Label title = new Label(agent.label()); title.getStyleClass().add("mini-title");
            status.getStyleClass().add("mini-status");
            VBox name = new VBox(2, title, status); name.setMinWidth(96);
            primary.getStyleClass().add("mini-value"); secondary.getStyleClass().add("mini-value");
            primaryBar.getStyleClass().add("mini-progress"); secondaryBar.getStyleClass().add("mini-progress");
            primaryBar.setPrefWidth(72); secondaryBar.setPrefWidth(72);
            Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
            root = new HBox(9, mark, name, grow, miniMetric("5 HORAS", primary, primaryBar),
                    miniMetric("SEMANAL", secondary, secondaryBar));
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().add("mini-agent");
            Tooltip.install(root, ageHint);
        }

        VBox miniMetric(String label, Label value, ProgressBar bar) {
            Label caption = new Label(label); caption.getStyleClass().add("mini-caption");
            return new VBox(3, caption, value, bar);
        }

        void update(AgentSnapshot process, UsageSnapshot usage) {
            status.setText(!process.online() ? "fora de execução"
                    : process.warmingUp() ? "ativo · medindo…"
                    : String.format(PT, "ativo · %.0f%% CPU", process.cpuPercent()));
            List<UsageWindow> windows = usage == null || !usage.available() || usage.stale() || usage.outdated()
                    ? List.of() : usage.windows();
            suffix = "";
            ageHint.setText(usage != null && usage.outdated()
                    ? agentLabel + " · leitura antiga ocultada; atualize no agente."
                    : agentLabel + " · limites publicados localmente pelo agente.");
            set(primary, primaryBar, windows.size() > 0 ? windows.get(0) : null);
            set(secondary, secondaryBar, windows.size() > 1 ? windows.get(1) : null);
        }

        void set(Label label, ProgressBar bar, UsageWindow window) {
            if (window == null || window.expired()) { label.setText("—"); bar.setProgress(0); }
            else {
                label.setText(String.format(PT, "%.0f%%", window.remainingPercent()) + suffix);
                bar.setProgress(window.remainingPercent() / 100.0);
            }
        }
    }
}
