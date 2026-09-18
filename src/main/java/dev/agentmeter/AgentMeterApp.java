package dev.agentmeter;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentSnapshot;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.service.AgentRegistry;
import dev.agentmeter.service.AppSettings;
import dev.agentmeter.service.ProcessMonitor;
import dev.agentmeter.service.SessionCounter;
import dev.agentmeter.service.TraySupport;
import dev.agentmeter.service.UsageMonitor;
import dev.agentmeter.ui.AboutView;
import dev.agentmeter.ui.CompactWidget;
import dev.agentmeter.ui.DashboardView;
import dev.agentmeter.ui.PluginsView;
import dev.agentmeter.ui.SettingsView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentMeterApp extends Application {
    private final AppSettings settings = new AppSettings();
    private final AgentRegistry registry = new AgentRegistry(settings);
    private final ProcessMonitor monitor = new ProcessMonitor(registry);
    private final SessionCounter sessionCounter = new SessionCounter(registry);
    private final UsageMonitor usageMonitor = new UsageMonitor(registry);
    private final TraySupport tray = new TraySupport();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-meter-monitor");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Map<String, Boolean> alerted = new HashMap<>();
    private DashboardView dashboard;
    private PluginsView plugins;
    private volatile int refreshSeconds;
    private boolean trayAvailable;
    private CompactWidget compactWidget;

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        refreshSeconds = settings.refreshSeconds();
        List<AgentDefinition> primaryAgents = registry.primary();

        DashboardView dashboardView = new DashboardView(primaryAgents, monitor.logicalCores());
        dashboard = dashboardView;
        SettingsView settingsView = new SettingsView(settings, registry);
        PluginsView pluginsView = new PluginsView(registry);
        plugins = pluginsView;
        AboutView aboutView = new AboutView();

        StackPane pages = new StackPane(dashboardView, pluginsView, settingsView, aboutView);
        dashboardView.setVisible(true);
        pluginsView.setVisible(false);
        settingsView.setVisible(false);
        aboutView.setVisible(false);

        Button overview = navButton("⌁", "Visão geral", true);
        Button agents = navButton("✦", "Agentes e plugins", false);
        Button config = navButton("≡", "Configurações", false);
        Button data = navButton("◇", "Fontes de dados", false);
        Button[] navButtons = {overview, agents, config, data};
        overview.setOnAction(event -> showPage(dashboardView, pages, overview, navButtons));
        agents.setOnAction(event -> showPage(pluginsView, pages, agents, navButtons));
        config.setOnAction(event -> showPage(settingsView, pages, config, navButtons));
        data.setOnAction(event -> showPage(aboutView, pages, data, navButtons));

        Label logoMark = new Label("AM"); logoMark.getStyleClass().add("logo-mark");
        Label logo = new Label("AGENT\nMETER"); logo.getStyleClass().add("logo-text");
        HBox brand = new HBox(10, logoMark, logo);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label section = new Label("MONITORAMENTO"); section.getStyleClass().add("nav-section");
        Region grow = new Region(); VBox.setVgrow(grow, Priority.ALWAYS);
        Label privacy = new Label("●  LOCAL E PRIVADO"); privacy.getStyleClass().add("privacy-badge");
        Button compact = navButton("▣", "Modo compacto", false);
        compact.setOnAction(event -> showCompact(stage));
        Label version = new Label("v1.1.1 · Java 17"); version.getStyleClass().add("nav-version");
        VBox side = new VBox(12, brand, new Separator(), section, overview, agents, config, data, grow, compact, privacy, version);
        side.setPadding(new Insets(24, 17, 22, 17));
        side.setPrefWidth(230);
        side.getStyleClass().add("sidebar");

        BorderPane root = new BorderPane(pages, null, null, null, side);
        Scene scene = new Scene(root, 1220, 790);
        String stylesheet = getClass().getResource("/dev/agentmeter/app.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setTitle("Agent Meter — Monitor de IA");
        stage.setMinWidth(820);
        stage.setMinHeight(620);
        compactWidget = new CompactWidget(stylesheet, primaryAgents, () -> expand(stage), this::shutdown);
        compactWidget.setAlwaysOnTop(settings.widgetAlwaysOnTop());
        stage.setOnCloseRequest(event -> {
            if (settings.minimizeToTray() && trayAvailable) {
                event.consume();
                stage.hide();
                compactWidget.show();
            } else shutdown();
        });

        trayAvailable = tray.install(stage, this::shutdown);
        settingsView.setTrayAvailable(trayAvailable);
        settingsView.setOnSave(() -> {
            refreshSeconds = settings.refreshSeconds();
            compactWidget.setAlwaysOnTop(settings.widgetAlwaysOnTop());
            registry.refresh();
        });
        // Útil para gerar as imagens da documentação sem interação manual.
        String initialPage = System.getProperty("agentmeter.page", "");
        switch (initialPage) {
            case "agentes" -> showPage(pluginsView, pages, agents, navButtons);
            case "config" -> showPage(settingsView, pages, config, navButtons);
            case "dados" -> showPage(aboutView, pages, data, navButtons);
            default -> { }
        }
        scheduleCollection();

        String compactCapture = System.getProperty("agentmeter.compactScreenshot");
        String dashboardCapture = System.getProperty("agentmeter.screenshot");
        if (compactCapture != null) {
            compactWidget.show();
            compactWidget.snapshot(compactCapture, this::shutdown);
        } else if (dashboardCapture != null) {
            stage.show();
        } else if (settings.startMinimized() && trayAvailable) {
            // permanece apenas na bandeja
        } else if (settings.compactOnLaunch()) compactWidget.show();
        else stage.show();
        if (compactCapture == null) createQaSnapshotIfRequested(root);
    }

    /**
     * Coleta periódica. Reagenda a cada ciclo em vez de dormir dentro da tarefa,
     * e captura qualquer Throwable: uma exceção escapando cancelaria a tarefa em
     * silêncio e a interface congelaria mostrando números antigos como atuais.
     */
    private void scheduleCollection() {
        executor.schedule(this::collectOnce, 0, TimeUnit.MILLISECONDS);
    }

    private void collectOnce() {
        if (stopped.get()) return;
        String failure = null;
        try {
            registry.refresh();
            ProcessMonitor.Reading reading = monitor.sample();
            Map<String, Integer> sessions = sessionCounter.countRecent();
            Map<String, UsageSnapshot> usage = usageMonitor.sample();
            tray.updateUsageTooltip(usage, registry.all());
            Platform.runLater(() -> {
                dashboard.update(reading, sessions, usage, null);
                plugins.update(reading);
                compactWidget.update(reading, usage);
                checkAlerts(reading);
            });
        } catch (Throwable error) {
            failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            System.err.println("Falha temporária na coleta: " + failure);
            String message = failure;
            Platform.runLater(() -> dashboard.showCollectionFailure(message));
        } finally {
            if (!stopped.get()) {
                executor.schedule(this::collectOnce, Math.max(1, refreshSeconds), TimeUnit.SECONDS);
            }
        }
    }

    private void checkAlerts(ProcessMonitor.Reading reading) {
        if (!settings.notifications()) return;
        for (AgentSnapshot snapshot : reading.agents().values()) {
            boolean over = snapshot.online() && !snapshot.warmingUp() && snapshot.cpuPercent() >= settings.alertCpu();
            boolean wasOver = alerted.getOrDefault(snapshot.agentId(), false);
            if (over && !wasOver) {
                AgentDefinition agent = registry.byId(snapshot.agentId());
                String label = agent == null ? snapshot.agentId() : agent.label();
                tray.notify("Agent Meter", label + " atingiu " + Math.round(snapshot.cpuPercent()) + "% de CPU.");
            }
            alerted.put(snapshot.agentId(), over);
        }
    }

    private Button navButton(String icon, String text, boolean active) {
        Button button = new Button(icon + "   " + text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("nav-button");
        if (active) button.getStyleClass().add("active");
        return button;
    }

    private void showPage(Node selected, StackPane pages, Button active, Button[] buttons) {
        pages.getChildren().forEach(node -> node.setVisible(node == selected));
        for (Button button : buttons) button.getStyleClass().remove("active");
        active.getStyleClass().add("active");
    }

    private void shutdown() {
        if (!stopped.compareAndSet(false, true)) return;
        tray.remove();
        if (compactWidget != null) compactWidget.hide();
        executor.shutdownNow();
        Platform.exit();
    }

    private void showCompact(Stage mainStage) {
        mainStage.hide();
        compactWidget.show();
    }

    private void expand(Stage mainStage) {
        compactWidget.hide();
        mainStage.show();
        mainStage.toFront();
        mainStage.requestFocus();
    }

    private void createQaSnapshotIfRequested(Parent root) {
        String target = System.getProperty("agentmeter.screenshot");
        if (target == null || target.isBlank()) return;
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        delay.setOnFinished(event -> {
            WritableImage image = root.snapshot(null, null);
            BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < output.getHeight(); y++) {
                for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
            try { ImageIO.write(output, "png", new File(target)); }
            catch (IOException error) { System.err.println("Não foi possível salvar a captura de QA: " + error.getMessage()); }
            shutdown();
        });
        delay.play();
    }

    @Override public void stop() { shutdown(); }
    public static void main(String[] args) { launch(args); }
}
