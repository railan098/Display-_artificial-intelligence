package dev.agentmeter.service;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.model.UsageWindow;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TraySupport {
    private TrayIcon icon;

    public boolean install(Stage stage, Runnable quitAction) {
        if (!SystemTray.isSupported()) return false;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Abrir Agent Meter");
            open.addActionListener(event -> Platform.runLater(() -> show(stage)));
            MenuItem quit = new MenuItem("Sair");
            quit.addActionListener(event -> Platform.runLater(quitAction));
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);
            icon = new TrayIcon(createImage(), "Agent Meter", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> Platform.runLater(() -> show(stage)));
            SystemTray.getSystemTray().add(icon);
            return true;
        } catch (AWTException | SecurityException ignored) {
            return false;
        }
    }

    private void show(Stage stage) {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public boolean available() { return icon != null; }

    /**
     * Atualiza o texto exibido ao repousar o mouse sobre o ícone da bandeja.
     * Percentuais antigos nunca entram no tooltip: nesse caso aparece um traço
     * e a instrução para atualizar a fonte oficial.
     */
    public void updateUsageTooltip(Map<String, UsageSnapshot> usage, List<AgentDefinition> agents) {
        if (icon == null) return;
        String tooltip = buildUsageTooltip(usage, agents);
        EventQueue.invokeLater(() -> {
            TrayIcon current = icon;
            if (current != null) current.setToolTip(tooltip);
        });
    }

    static String buildUsageTooltip(Map<String, UsageSnapshot> usage, List<AgentDefinition> agents) {
        StringBuilder text = new StringBuilder("Agent Meter");
        for (AgentDefinition agent : agents) {
            if (agent.usageSource() == AgentDefinition.UsageSource.NONE) continue;
            UsageSnapshot snapshot = usage == null ? null : usage.get(agent.id());
            text.append('\n').append(shortLabel(agent)).append(" 5h: ");
            UsageWindow window = fiveHourWindow(snapshot);
            if (window == null) {
                text.append("—");
                if ("claude".equals(agent.id())) text.append(" (use /usage)");
            } else {
                text.append(String.format(Locale.ROOT, "%.0f%% restante", window.remainingPercent()));
            }
        }
        return text.toString();
    }

    private static UsageWindow fiveHourWindow(UsageSnapshot snapshot) {
        if (snapshot == null || !snapshot.available() || snapshot.stale() || snapshot.outdated()) return null;
        return snapshot.windows().stream()
                .filter(window -> !window.expired())
                .filter(window -> window.windowMinutes() == 300 || window.label().contains("5 horas"))
                .findFirst().orElse(null);
    }

    private static String shortLabel(AgentDefinition agent) {
        return "claude".equals(agent.id()) ? "Claude" : agent.label();
    }

    /** Retorna falso quando não há bandeja para exibir o aviso. */
    public boolean notify(String title, String message) {
        if (icon == null) return false;
        icon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
        return true;
    }

    public void remove() {
        if (icon != null && SystemTray.isSupported()) SystemTray.getSystemTray().remove(icon);
        icon = null;
    }

    private Image createImage() {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(19, 22, 20));
        g.fillRoundRect(2, 2, 60, 60, 16, 16);
        g.setColor(new Color(190, 255, 46));
        g.fillRoundRect(8, 8, 48, 48, 12, 12);
        g.setColor(new Color(15, 18, 16));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
        g.drawString("A", 20, 43);
        g.dispose();
        return image;
    }
}
