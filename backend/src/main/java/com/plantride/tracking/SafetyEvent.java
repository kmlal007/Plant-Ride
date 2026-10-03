package com.plantride.tracking;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "safety_event")
@Getter
@Setter
public class SafetyEvent {

    public static final String OVER_SPEED = "OVER_SPEED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    private Long vehicleId;
    private String eventType;
    private Double lat;
    private Double lng;
    private Double speedKmh;
    private Instant occurredAt;
    private boolean acknowledged;
}
