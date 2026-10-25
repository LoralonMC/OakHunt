package dev.oakheart.oakhunt.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command dates are UTC, the server's own clock. Accepts {@code 2026-10-31 18:00},
 * {@code 2026-10-31} (midnight) or a relative {@code +2h}, {@code +1d12h}, {@code +30m}.
 */
public final class TimeUtil {

    private static final Pattern RELATIVE = Pattern.compile("\\+((\\d+)d)?((\\d+)h)?((\\d+)m)?");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private TimeUtil() {}

    /**
     * @return epoch millis, or null if the input isn't a recognised date
     */
    public static Long parse(String input, long now) {
        if (input == null) return null;
        String text = input.trim().replace('T', ' ');
        Matcher relative = RELATIVE.matcher(text.toLowerCase(Locale.ROOT));
        if (relative.matches() && text.length() > 1) {
            long days = relative.group(2) != null ? Long.parseLong(relative.group(2)) : 0;
            long hours = relative.group(4) != null ? Long.parseLong(relative.group(4)) : 0;
            long minutes = relative.group(6) != null ? Long.parseLong(relative.group(6)) : 0;
            return now + ((days * 24 + hours) * 60 + minutes) * 60_000L;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through to date-only
        }
        try {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String formatUtc(long epochMillis) {
        return DISPLAY.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatMonth(long epochMillis) {
        return MONTH.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatDay(long epochMillis) {
        return DAY.format(Instant.ofEpochMilli(epochMillis));
    }

    /** "2d 4h", "3h 12m", "8m", "45s". */
    public static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
