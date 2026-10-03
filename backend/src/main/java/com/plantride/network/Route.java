package com.plantride.network;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A fixed route running on a metro-style headway: a trip leaves the first stop every
 * {@code headwayMinutes} from {@code firstDeparture} to {@code lastDeparture} on {@code daysOfWeek}.
 */
@Entity
@Table(name = "route")
@Getter
@Setter
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private RouteKind routeKind;

    private LocalTime firstDeparture;
    private LocalTime lastDeparture;
    private int headwayMinutes;
    /** ISO day numbers, Monday=1 ... Sunday=7, comma separated. */
    private String daysOfWeek = "1,2,3,4,5,6,7";
    private boolean active = true;
    /** Fixed monthly running cost; distributed per the plant's shuttle cost allocation policy. */
    private BigDecimal monthlyCost;
}
