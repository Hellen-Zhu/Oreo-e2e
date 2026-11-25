package com.example.module.oreo.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateUtil {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter UTC_DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final Pattern DATE_TIME =
            Pattern.compile("[Tt]([+-]\\d{1,5})?(?: ((?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?))?");

    private DateUtil() {}

    public static Instant now() {
        return Instant.now();
    }

    /** Today's date in UTC, whatever this machine's time zone, as the test data's {@code ${date:T}} counts it. */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public static String format(Instant instant) {
        return TS_FMT.format(instant);
    }

    public static String format(Instant instant, String pattern) {
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    public static long elapsedMs(Instant start, Instant end) {
        return end.toEpochMilli() - start.toEpochMilli();
    }

    public static String formatDuration(long durationMs) {
        long minutes = durationMs / 60_000;
        long seconds = (durationMs % 60_000) / 1_000;
        long millis  = durationMs % 1_000;
        return String.format("%dm %ds %dms (%dms total)", minutes, seconds, millis, durationMs);
    }

    public static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    public static LocalDate plusBusinessDays(LocalDate date, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0");
        }

        LocalDate result = date;
        for (int i = 0; i < days; i++) {
            result = nextBusinessDay(result);
        }
        return result;
    }

    public static String format(LocalDate date, String pattern) {
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Resolve a relative date expression to an absolute {@code yyyy-MM-dd} string, counted from the date of
     * {@code now} in UTC, whatever this machine's time zone.
     *
     * <ul>
     *   <li>{@code T}   — today</li>
     *   <li>{@code T+N} — today plus N calendar days</li>
     *   <li>{@code T-N} — today minus N calendar days</li>
     *   <li>Any other value — returned as-is (pass-through for already-absolute dates)</li>
     * </ul>
     *
     * @param expression date expression of the test data's {@code ${date:...}}, e.g. {@code "T+3"}
     * @param now        the moment {@code T} stands for
     * @return resolved {@code yyyy-MM-dd} string, or the original value if not a T-expression
     */
    public static String resolveDate(String expression, Instant now) {
        if (expression == null || expression.trim().isEmpty()) {
            return expression;
        }
        String s = expression.trim();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        if ("T".equalsIgnoreCase(s)) {
            return today.format(DATE_FMT);
        }
        if (s.toUpperCase().startsWith("T+")) {
            int days = Integer.parseInt(s.substring(2));
            return today.plusDays(days).format(DATE_FMT);
        }
        if (s.toUpperCase().startsWith("T-")) {
            int days = Integer.parseInt(s.substring(2));
            return today.minusDays(days).format(DATE_FMT);
        }
        return s;
    }

    /**
     * Resolve a relative date and time expression to a UTC time, {@code yyyy-MM-dd'T'HH:mm:ss'Z'}
     * ({@code 2026-11-24T09:00:00Z}), whatever this machine's time zone.
     *
     * <ul>
     *   <li>{@code T}, {@code T+N}, {@code T-N} — {@code now}, to the second, plus or minus N days</li>
     *   <li>{@code T+N HH:mm}, {@code T+N HH:mm:ss} (also after {@code T}, {@code T-N}) — that UTC time on the UTC
     *       date of {@code now} plus or minus N days</li>
     * </ul>
     *
     * @param expression date and time expression of the test data's {@code ${datetime:...}}, e.g. {@code "T+1 09:00"}
     * @param now        the moment {@code T} stands for
     * @throws IllegalArgumentException when {@code expression} is none of these
     */
    public static String resolveDateTime(String expression, Instant now) {
        Matcher m = DATE_TIME.matcher(expression == null ? "" : expression.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("'" + expression + "' is not a date and time:"
                    + " write T, T+N or T-N, optionally followed by a UTC time HH:mm or HH:mm:ss");
        }
        long days = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        Instant at = m.group(2) == null
                ? now.truncatedTo(ChronoUnit.SECONDS).plus(days, ChronoUnit.DAYS)
                : now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(days)
                        .atTime(LocalTime.parse(m.group(2))).toInstant(ZoneOffset.UTC);
        return UTC_DATE_TIME_FMT.format(at);
    }
}
