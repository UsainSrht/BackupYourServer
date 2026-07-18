package me.usainsrht.backupyourserver.util;

import java.time.Duration;
import java.util.Locale;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String format(final Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "now";
        }

        final long totalSeconds = duration.getSeconds();
        final long days = totalSeconds / 86_400L;
        final long hours = (totalSeconds % 86_400L) / 3_600L;
        final long minutes = (totalSeconds % 3_600L) / 60L;
        final long seconds = totalSeconds % 60L;

        if (days > 0L) {
            return formatUnit(days, "day", "days")
                    + (hours > 0L ? " " + formatUnit(hours, "hour", "hours") : "");
        }
        if (hours > 0L) {
            return formatUnit(hours, "hour", "hours")
                    + (minutes > 0L ? " " + formatUnit(minutes, "minute", "minutes") : "");
        }
        if (minutes > 0L) {
            return formatUnit(minutes, "minute", "minutes")
                    + (seconds > 0L ? " " + formatUnit(seconds, "second", "seconds") : "");
        }
        return formatUnit(seconds, "second", "seconds");
    }

    private static String formatUnit(final long value, final String singular, final String plural) {
        return value + " " + (value == 1L ? singular : plural);
    }

    public static Duration parseDuration(final long value, final String unit) {
        if (value <= 0L) {
            return Duration.ZERO;
        }
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "hour", "hours" -> Duration.ofHours(value);
            case "day", "days" -> Duration.ofDays(value);
            default -> Duration.ZERO;
        };
    }
}
