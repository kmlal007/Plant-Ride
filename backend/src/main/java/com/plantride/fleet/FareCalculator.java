package com.plantride.fleet;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FareCalculator {

    private FareCalculator() {
    }

    /** fare = max(minimum, base + perKm x km + perMinute x minutes), rounded to paise. */
    public static BigDecimal fare(RateCard rate, BigDecimal km, int minutes) {
        BigDecimal fare = rate.getBaseFare()
                .add(rate.getPerKm().multiply(km))
                .add(rate.getPerMinute().multiply(BigDecimal.valueOf(minutes)));
        return fare.max(rate.getMinimumFare()).setScale(2, RoundingMode.HALF_UP);
    }
}
