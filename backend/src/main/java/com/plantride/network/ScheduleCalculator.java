package com.plantride.network;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure timetable maths for headway-based routes. */
public final class ScheduleCalculator {

    private ScheduleCalculator() {
    }

    /**
     * Next scheduled arrivals of {@code route} at a stop that is {@code offsetMinutes} after the
     * first stop, at or after {@code from} (plant local time). Looks at today and tomorrow, so
     * late-night queries still return the first trips of the next service day.
     */
    public static List<LocalDateTime> nextArrivals(Route route, int offsetMinutes, LocalDateTime from, int limit) {
        List<LocalDateTime> result = new ArrayList<>();
        if (route.getHeadwayMinutes() <= 0 || limit <= 0) {
            return result;
        }
        Set<DayOfWeek> days = parseDays(route.getDaysOfWeek());
        // Start a day early so trips that departed before midnight but arrive after it are included.
        for (LocalDate date = from.toLocalDate().minusDays(1);
             !date.isAfter(from.toLocalDate().plusDays(1)) && result.size() < limit;
             date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            LocalDateTime departure = date.atTime(route.getFirstDeparture());
            LocalDateTime last = date.atTime(route.getLastDeparture());
            while (!departure.isAfter(last) && result.size() < limit) {
                LocalDateTime arrival = departure.plusMinutes(offsetMinutes);
                if (!arrival.isBefore(from)) {
                    result.add(arrival);
                }
                departure = departure.plusMinutes(route.getHeadwayMinutes());
            }
        }
        return result;
    }

    static Set<DayOfWeek> parseDays(String days) {
        if (days == null || days.isBlank()) {
            return Set.of(DayOfWeek.values());
        }
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> DayOfWeek.of(Integer.parseInt(s)))
                .collect(Collectors.toSet());
    }
}
