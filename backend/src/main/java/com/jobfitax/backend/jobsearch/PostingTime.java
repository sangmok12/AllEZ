package com.jobfitax.backend.jobsearch;

import java.time.*;
import java.util.regex.Pattern;

/** A sorting estimate at the source's published precision, not an exact creation timestamp. */
final class PostingTime {
    private static final Pattern RELATIVE = Pattern.compile("(\\d+)(초|분|시간|일)전");
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})[./-])?(\\d{1,2})[./-](\\d{1,2})(?:[T ]?(\\d{1,2}):(\\d{2}))?");
    static Instant estimate(String text, Instant observedAt) {
        if (text == null) return Instant.MIN;
        String value = text.replaceAll("\\s+", "");
        var now = observedAt.atZone(ZoneId.of("Asia/Seoul"));
        var relative = RELATIVE.matcher(value);
        if (relative.find()) {
            long amount = Long.parseLong(relative.group(1));
            return switch (relative.group(2)) {
                case "초" -> observedAt.minusSeconds(amount);
                case "분" -> observedAt.minusSeconds(amount * 60);
                case "시간" -> observedAt.minusSeconds(amount * 3600);
                default -> now.toLocalDate().minusDays(amount).atStartOfDay(now.getZone()).toInstant();
            };
        }
        if (value.contains("방금")) return observedAt;
        var clock = CLOCK.matcher(value);
        var date = DATE.matcher(value);
        try {
            if (date.find()) {
                int year = date.group(1) == null ? now.getYear() : Integer.parseInt(date.group(1));
                var day = LocalDate.of(year, Integer.parseInt(date.group(2)), Integer.parseInt(date.group(3)));
                if (date.group(1) == null && day.isAfter(now.toLocalDate())) day = day.minusYears(1);
                return day.atTime(date.group(4) == null ? 0 : Integer.parseInt(date.group(4)),
                        date.group(5) == null ? 0 : Integer.parseInt(date.group(5))).atZone(now.getZone()).toInstant();
            }
            if (clock.find()) return now.toLocalDate().atTime(Integer.parseInt(clock.group(1)),
                    Integer.parseInt(clock.group(2))).atZone(now.getZone()).toInstant();
            if (value.contains("오늘")) return now.toLocalDate().atStartOfDay(now.getZone()).toInstant();
            if (value.contains("어제")) return now.toLocalDate().minusDays(1).atStartOfDay(now.getZone()).toInstant();
        } catch (DateTimeException | NumberFormatException ignored) { /* Unrecognized dates sort last. */ }
        return Instant.MIN;
    }
}
