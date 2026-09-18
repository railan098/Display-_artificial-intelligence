package dev.agentmeter.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class AboutView extends ScrollPane {
    public AboutView() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("page-scroll");
        VBox content = new VBox(24);
        content.getStyleClass().add("page");
        content.setPadding(new Insets(34, 38, 42, 38));

        Label eyebrow = new Label("TRANSPARÊNCIA E DIAGNÓSTICO");
        eyebrow.getStyleClass().add("eyebrow-accent");
        Label title = new Label("Fontes de dados");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Saiba exatamente de onde vem cada número exibido.");
        subtitle.getStyleClass().add("page-subtitle");

        VBox real = panel("MEDIÇÕES REAIS", "Coletado neste computador",
                item("CPU do agente", "Soma da carga dos processos identificados. 100% equivale a um núcleo ocupado; o cartão também mostra a fração da máquina inteira."),
                item("Identificação de processo", "Nome do executável, caminho e linha de comando são comparados separadamente. Um processo que apenas cita a pasta do agente não é contado como o agente."),
                item("Memória", "Soma da memória residente de cada processo identificado."),
                item("Tempo ativo", "Tempo transcorrido desde o início do processo mais antigo do agente."),
                item("Sessões em 24h", "Contagem de arquivos de sessão recentes em ~/.codex/sessions e ~/.claude/projects, reaproveitada por 30 segundos."),
                item("Limites do Codex", "Lê o último evento rate_limits publicado pelo Codex, com janelas de 5 horas e semanal."),
                item("Limites do Claude", "Lê o cache de utilização mantido pelo próprio Claude Code e sinaliza quando estiver antigo."));
        VBox unavailable = panel("LIMITES DO PROVEDOR", "Sem números inventados",
                item("Disponibilidade", "Os limites aparecem somente depois que o agente publica uma medição local. Ausência e dados antigos são identificados na interface."),
                item("Tokens restantes", "Não são convertidos em cota de assinatura. Cada janela usa apenas o percentual fornecido pelo provedor."),
                item("Idade da medição", "O percentual vem com a hora em que o agente o gravou. Passados 5% da duração da janela, o cartão mostra a idade em vez de afirmar que o dado é atual."),
                item("Janela vencida", "Cada janela é avaliada sozinha: se o ciclo de 5 horas virou, só ele é ocultado e o semanal continua visível."),
                item("Falha de coleta", "Se uma coleta falhar, o painel avisa em vez de manter o último número como se fosse atual."),
                item("Privacidade", "A coleta é local. Chaves, prompts e conteúdo das sessões não são enviados nem exibidos."));

        Label app = new Label("AGENT METER  1.1.1");
        app.getStyleClass().add("brand-stamp");
        Label runtime = new Label("Java " + System.getProperty("java.version") + " · " + System.getProperty("os.name") + " · " + System.getProperty("os.arch"));
        runtime.getStyleClass().add("support-copy");
        VBox footer = new VBox(6, app, runtime);
        footer.getStyleClass().addAll("surface", "about-footer");
        content.getChildren().addAll(new VBox(5, eyebrow, title, subtitle), real, unavailable, footer);
        setContent(content);
    }

    private VBox panel(String eyebrowText, String titleText, HBox... items) {
        Label eyebrow = new Label(eyebrowText); eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label(titleText); title.getStyleClass().add("section-title");
        VBox box = new VBox(0, eyebrow, title);
        VBox.setMargin(title, new Insets(5, 0, 16, 0));
        for (HBox item : items) box.getChildren().add(item);
        box.getStyleClass().addAll("surface", "settings-section");
        return box;
    }

    private HBox item(String title, String copy) {
        Label check = new Label("✓"); check.getStyleClass().add("check-mark");
        Label name = new Label(title); name.getStyleClass().add("row-title");
        Label description = new Label(copy); description.setWrapText(true); description.getStyleClass().add("support-copy");
        VBox body = new VBox(3, name, description); HBox.setHgrow(body, Priority.ALWAYS);
        HBox row = new HBox(14, check, body); row.getStyleClass().add("settings-row");
        return row;
    }
}
