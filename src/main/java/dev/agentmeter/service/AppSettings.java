package dev.agentmeter.service;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class AppSettings {
    private static final Preferences STORE = Preferences.userRoot().node("dev/agentmeter");

    private int refreshSeconds;
    private boolean startMinimized;
    private boolean minimizeToTray;
    private boolean notifications;
    private boolean compactNumbers;
    private int alertCpu;
    private String theme;
    private String codexAliases;
    private String claudeAliases;
    private boolean compactOnLaunch;
    private boolean widgetAlwaysOnTop;

    public AppSettings() { reload(); }

    public void reload() {
        refreshSeconds = STORE.getInt("refreshSeconds", 2);
        startMinimized = STORE.getBoolean("startMinimized", false);
        minimizeToTray = STORE.getBoolean("minimizeToTray", true);
        notifications = STORE.getBoolean("notifications", true);
        compactNumbers = STORE.getBoolean("compactNumbers", false);
        alertCpu = STORE.getInt("alertCpu", 85);
        theme = STORE.get("theme", "Escuro");
        codexAliases = STORE.get("codexAliases", "");
        claudeAliases = STORE.get("claudeAliases", "");
        compactOnLaunch = STORE.getBoolean("compactOnLaunch", true);
        widgetAlwaysOnTop = STORE.getBoolean("widgetAlwaysOnTop", true);
    }

    public void save() {
        STORE.putInt("refreshSeconds", refreshSeconds);
        STORE.putBoolean("startMinimized", startMinimized);
        STORE.putBoolean("minimizeToTray", minimizeToTray);
        STORE.putBoolean("notifications", notifications);
        STORE.putBoolean("compactNumbers", compactNumbers);
        STORE.putInt("alertCpu", alertCpu);
        STORE.put("theme", theme);
        STORE.put("codexAliases", codexAliases);
        STORE.put("claudeAliases", claudeAliases);
        STORE.putBoolean("compactOnLaunch", compactOnLaunch);
        STORE.putBoolean("widgetAlwaysOnTop", widgetAlwaysOnTop);
        // Sem flush as preferências podiam se perder se o aplicativo encerrasse
        // antes da gravação em segundo plano do java.util.prefs.
        try { STORE.flush(); } catch (BackingStoreException ignored) { }
    }

    public int refreshSeconds() { return refreshSeconds; }
    public void setRefreshSeconds(int value) { refreshSeconds = Math.max(1, Math.min(30, value)); }
    public boolean startMinimized() { return startMinimized; }
    public void setStartMinimized(boolean value) { startMinimized = value; }
    public boolean minimizeToTray() { return minimizeToTray; }
    public void setMinimizeToTray(boolean value) { minimizeToTray = value; }
    public boolean notifications() { return notifications; }
    public void setNotifications(boolean value) { notifications = value; }
    public boolean compactNumbers() { return compactNumbers; }
    public void setCompactNumbers(boolean value) { compactNumbers = value; }
    public int alertCpu() { return alertCpu; }
    public void setAlertCpu(int value) { alertCpu = Math.max(10, Math.min(100, value)); }
    public String theme() { return theme; }
    public void setTheme(String value) { theme = value == null ? "Escuro" : value; }
    public String codexAliases() { return codexAliases; }
    public void setCodexAliases(String value) { codexAliases = value == null ? "" : value.trim(); }
    public String claudeAliases() { return claudeAliases; }
    public void setClaudeAliases(String value) { claudeAliases = value == null ? "" : value.trim(); }
    public boolean compactOnLaunch() { return compactOnLaunch; }
    public void setCompactOnLaunch(boolean value) { compactOnLaunch = value; }
    public boolean widgetAlwaysOnTop() { return widgetAlwaysOnTop; }
    public void setWidgetAlwaysOnTop(boolean value) { widgetAlwaysOnTop = value; }
}
