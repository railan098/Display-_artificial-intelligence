package dev.agentmeter.ui;

import dev.agentmeter.service.AgentRegistry;
import dev.agentmeter.service.AppSettings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class SettingsView extends ScrollPane {
    private final ComboBox<Integer> refresh = new ComboBox<>();
    private final CheckBox startMinimized = new CheckBox();
    private final CheckBox tray = new CheckBox();
    private final CheckBox notifications = new CheckBox();
    private final CheckBox compact = new CheckBox();
    private final Slider threshold = new Slider(10, 100, 85);
    private final Label thresholdValue = new Label();
    private final TextField codexAliases = new TextField();
    private final TextField claudeAliases = new TextField();
    private final CheckBox compactOnLaunch = new CheckBox();
    private final CheckBox widgetAlwaysOnTop = new CheckBox();
    private final Label trayWarning = new Label();
    private Runnable onSave = () -> {};

    public SettingsView(AppSettings settings, AgentRegistry registry) {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("page-scroll");
        VBox content = new VBox(24);
        content.getStyleClass().add("page");
        content.setPadding(new Insets(34, 38, 42, 38));

        Label eyebrow = new Label("PREFERÊNCIAS DO APLICATIVO");
        eyebrow.getStyleClass().add("eyebrow-accent");
        Label title = new Label("Configurações");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Ajuste como o Agent Meter coleta, avisa e permanece disponível.");
        subtitle.getStyleClass().add("page-subtitle");
        content.getChildren().add(new VBox(5, eyebrow, title, subtitle));

        refresh.getItems().addAll(1, 2, 5, 10, 30);
        refresh.setValue(settings.refreshSeconds());
        refresh.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Integer value) { return value == null ? "" : value + (value == 1 ? " segundo" : " segundos"); }
            public Integer fromString(String value) { return Integer.parseInt(value.split(" ")[0]); }
        });
        content.getChildren().add(section("COLETA", "Telemetria", "Defina a frequência de atualização dos processos locais.",
                row("Intervalo de atualização", "Menor intervalo usa um pouco mais de CPU.", refresh)));

        codexAliases.setText(settings.codexAliases());
        codexAliases.setPromptText("ex.: meu-codex, wrapper-openai");
        claudeAliases.setText(settings.claudeAliases());
        claudeAliases.setPromptText("ex.: meu-claude, claude-wrapper");
        Label pluginHint = new Label("Para adicionar outros agentes (e não apenas apelidos), use a página Agentes e plugins: "
                + registry.pluginFile());
        pluginHint.setWrapText(true);
        pluginHint.getStyleClass().add("source-copy");
        content.getChildren().add(section("AGENTES", "Detecção personalizada", "Adicione nomes de processos separados por vírgula; a detecção padrão continua ativa.",
                row("Aliases do Codex", "Use apenas quando sua instalação inicia por um wrapper próprio.", codexAliases),
                row("Aliases do Claude", "O nome será procurado no comando e caminho do processo.", claudeAliases),
                row("Definições próprias", "Arquivo JSON com agentes adicionais.", pluginHint)));

        bind(startMinimized, settings.startMinimized());
        bind(tray, settings.minimizeToTray());
        bind(compactOnLaunch, settings.compactOnLaunch());
        bind(widgetAlwaysOnTop, settings.widgetAlwaysOnTop());
        content.getChildren().add(section("INICIALIZAÇÃO", "Comportamento da janela", "Controle como o aplicativo abre e fecha.",
                toggleRow("Abrir como caixa compacta", "O atalho inicia o monitor pequeno; use ↗ para expandir.", compactOnLaunch),
                toggleRow("Caixa sempre visível", "Mantém o monitor compacto acima das outras janelas.", widgetAlwaysOnTop),
                toggleRow("Iniciar minimizado", "Abre discretamente junto da sua rotina.", startMinimized),
                toggleRow("Minimizar para a bandeja", "Mantém a coleta ativa ao fechar a janela.", tray)));

        bind(notifications, settings.notifications());
        bind(compact, settings.compactNumbers());
        threshold.setValue(settings.alertCpu());
        threshold.setBlockIncrement(5);
        thresholdValue.getStyleClass().add("threshold-value");
        threshold.valueProperty().addListener((obs, old, value) -> thresholdValue.setText(Math.round(value.doubleValue()) + "%"));
        thresholdValue.setText(settings.alertCpu() + "%");
        HBox thresholdControl = new HBox(12, threshold, thresholdValue);
        HBox.setHgrow(threshold, Priority.ALWAYS);
        trayWarning.setWrapText(true);
        trayWarning.getStyleClass().add("source-copy");
        trayWarning.setManaged(false);
        trayWarning.setVisible(false);
        content.getChildren().add(section("ALERTAS", "Sinais e números", "Escolha quando o monitor deve chamar sua atenção.",
                toggleRow("Notificações do sistema", "Avisa quando um agente ultrapassa o limite de CPU.", notifications),
                row("Bandeja do sistema", "Estado da área de notificação neste ambiente.", trayWarning),
                row("Limite de CPU", "Aplicado individualmente a cada agente.", thresholdControl),
                toggleRow("Números compactos", "Reserva para uma visualização condensada futura.", compact)));

        Label note = new Label("As preferências são armazenadas no perfil do seu sistema operacional. Nenhuma chave ou telemetria é enviada para servidores externos.");
        note.setWrapText(true);
        note.getStyleClass().add("support-copy");
        Button save = new Button("Salvar alterações");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            settings.setRefreshSeconds(refresh.getValue());
            settings.setStartMinimized(startMinimized.isSelected());
            settings.setMinimizeToTray(tray.isSelected());
            settings.setCompactOnLaunch(compactOnLaunch.isSelected());
            settings.setWidgetAlwaysOnTop(widgetAlwaysOnTop.isSelected());
            settings.setNotifications(notifications.isSelected());
            settings.setCompactNumbers(compact.isSelected());
            settings.setAlertCpu((int) Math.round(threshold.getValue()));
            settings.setCodexAliases(codexAliases.getText());
            settings.setClaudeAliases(claudeAliases.getText());
            settings.save();
            save.setText("Salvo ✓");
            onSave.run();
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pause.setOnFinished(e -> save.setText("Salvar alterações"));
            pause.play();
        });
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
        content.getChildren().add(new HBox(16, note, push, save));
        setContent(content);
    }

    private void bind(CheckBox box, boolean value) { box.setSelected(value); box.getStyleClass().add("switch"); }

    private VBox section(String eyebrowText, String titleText, String copy, HBox... rows) {
        Label eyebrow = new Label(eyebrowText); eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label(titleText); title.getStyleClass().add("section-title");
        Label subtitle = new Label(copy); subtitle.getStyleClass().add("support-copy");
        VBox box = new VBox(0, eyebrow, title, subtitle);
        VBox.setMargin(title, new Insets(5, 0, 2, 0));
        VBox.setMargin(subtitle, new Insets(0, 0, 18, 0));
        for (HBox row : rows) box.getChildren().add(row);
        box.getStyleClass().addAll("surface", "settings-section");
        return box;
    }

    private HBox toggleRow(String title, String copy, CheckBox control) { return row(title, copy, control); }

    private HBox row(String title, String copy, javafx.scene.Node control) {
        Label name = new Label(title); name.getStyleClass().add("row-title");
        Label description = new Label(copy); description.getStyleClass().add("support-copy");
        VBox text = new VBox(3, name, description);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(24, text, control);
        row.getStyleClass().add("settings-row");
        return row;
    }

    public void setOnSave(Runnable callback) { onSave = callback == null ? () -> {} : callback; }

    /** Sem bandeja não há como exibir notificação; dizer isso evita alerta que nunca chega. */
    public void setTrayAvailable(boolean available) {
        trayWarning.setText(available
                ? "Bandeja disponível: as notificações serão exibidas pelo sistema."
                : "Este ambiente não expõe bandeja do sistema; as notificações não serão exibidas e fechar a janela encerra o aplicativo.");
        trayWarning.setManaged(true);
        trayWarning.setVisible(true);
    }
}
