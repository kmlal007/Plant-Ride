package com.plantride.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ScheduleCalculatorTest {

    private static Route route(String first, String last, int headway, String days) {
        Route r = new Route();
        r.setFirstDeparture(LocalTime.parse(first));
        r.setLastDeparture(LocalTime.parse(last));
        r.setHeadwayMinutes(headway);
        r.setDaysOfWeek(days);
        return r;
    }

    @Test
    void returnsNextArrivalsIncludingStopOffset() {
        Route r = route("06:00", "22:00", 15, "1,2,3,4,5,6,7");
        // 2026-10-05 is a Monday. Stop is 9 minutes after first stop: arrivals at :09, :24, :39, :54.
        List<LocalDateTime> next = ScheduleCalculator.nextArrivals(r, 9, LocalDateTime.parse("2026-10-05T10:10"), 3);
        assertThat(next).containsExactly(
                LocalDateTime.parse("2026-10-05T10:24"),
                LocalDateTime.parse("2026-10-05T10:39"),
                LocalDateTime.parse("2026-10-05T10:54"));
    }

    @Test
    void includesArrivalExactlyNow() {
        Route r = route("06:00", "22:00", 15, null);
        assertThat(ScheduleCalculator.nextArrivals(r, 0, LocalDateTime.parse("2026-10-05T10:15"), 1))
                .containsExactly(LocalDateTime.parse("2026-10-05T10:15"));
    }

    @Test
    void rollsOverToNextServiceDayAfterLastTrip() {
        Route r = route("06:00", "22:00", 30, "1,2,3,4,5,6,7");
        assertThat(ScheduleCalculator.nextArrivals(r, 5, LocalDateTime.parse("2026-10-05T23:00"), 1))
                .containsExactly(LocalDateTime.parse("2026-10-06T06:05"));
    }

    @Test
    void skipsDaysWithoutService() {
        // Weekdays only; Saturday 2026-10-10 evening -> Monday 2026-10-12 morning is beyond the two-day window.
        Route r = route("07:00", "19:00", 60, "1,2,3,4,5");
        assertThat(ScheduleCalculator.nextArrivals(r, 0, LocalDateTime.parse("2026-10-09T20:00"), 1)).isEmpty();
        assertThat(ScheduleCalculator.nextArrivals(r, 0, LocalDateTime.parse("2026-10-11T20:00"), 1))
                .containsExactly(LocalDateTime.parse("2026-10-12T07:00"));
    }

    @Test
    void includesTripThatDepartedBeforeMidnightButArrivesAfter() {
        Route r = route("06:00", "23:50", 60, null);
        // The 23:00 departure reaches a stop 70 minutes later at 00:10 the next day.
        assertThat(ScheduleCalculator.nextArrivals(r, 70, LocalDateTime.parse("2026-10-06T00:00"), 1))
                .containsExactly(LocalDateTime.parse("2026-10-06T00:10"));
    }
}
