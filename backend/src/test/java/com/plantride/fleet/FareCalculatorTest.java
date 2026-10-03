package com.plantride.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FareCalculatorTest {

    private static RateCard rate() {
        RateCard r = new RateCard();
        r.setBaseFare(new BigDecimal("50"));
        r.setPerKm(new BigDecimal("14"));
        r.setPerMinute(new BigDecimal("1"));
        r.setMinimumFare(new BigDecimal("80"));
        return r;
    }

    @Test
    void computesBasePlusDistancePlusTime() {
        // 50 + 14 x 3.5 + 1 x 12 = 111
        assertThat(FareCalculator.fare(rate(), new BigDecimal("3.5"), 12)).isEqualByComparingTo("111.00");
    }

    @Test
    void appliesMinimumFare() {
        assertThat(FareCalculator.fare(rate(), new BigDecimal("0.5"), 2)).isEqualByComparingTo("80.00");
    }
}
