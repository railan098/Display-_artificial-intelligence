package dev.agentmeter.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UiFormat {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private UiFormat() {}

    public static String bytes(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(0, bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.forLanguageTag("pt-BR"), "%.0f MB", bytes / 1048576.0);
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f GB", bytes / 1073741824.0);
    }

    public static String duration(long millis) {
        Duration value = Duration.ofMillis(Math.max(0, millis));
        long hours = value.toHours();
        long minutes = value.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "min" : Math.max(1, minutes) + " min";
    }

    public static String clock(Instant instant) { return CLOCK.format(instant); }

    /** "há 24 min", "há 3h 10min" — para dizer a idade de uma medição. */
    public static String age(Duration value) {
        if (value == null || value.isNegative()) return "agora";
        long days = value.toDays();
        if (days > 0) return "há " + days + "d " + value.toHoursPart() + "h";
        long hours = value.toHours();
        if (hours > 0) return "há " + hours + "h " + value.toMinutesPart() + "min";
        long minutes = value.toMinutes();
        return minutes < 1 ? "agora" : "há " + minutes + " min";
    }

    public static String resetAt(Instant instant) {
        if (instant == null) return "sem horário de redefinição";
        Duration remaining = Duration.between(Instant.now(), instant);
        if (remaining.isNegative()) return "aguardando atualização";
        long days = remaining.toDays();
        long hours = remaining.toHoursPart();
        long minutes = remaining.toMinutesPart();
        if (days > 0) return "Redefine em " + days + "d " + hours + "h";
        if (remaining.toHours() > 0) return "Redefine em " + remaining.toHours() + "h " + minutes + "min";
        return "Redefine em " + Math.max(1, remaining.toMinutes()) + "min";
    }
}
