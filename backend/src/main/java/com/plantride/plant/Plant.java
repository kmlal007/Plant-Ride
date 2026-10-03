package com.plantride.plant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A site (plant) with its own operating rules. Every business record is scoped to one plant. */
@Entity
@Table(name = "plant")
@Getter
@Setter
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String timezone = "Asia/Kolkata";
    private Double centerLat;
    private Double centerLng;

    /** Plant speed limit; GPS fixes above this raise an OVER_SPEED safety event. */
    private int speedLimitKmh = 30;
    /** Only vehicles within this distance of the pickup are offered a ride. */
    private double dispatchRadiusKm = 10;
    /** A driver must accept an offered ride within this time, otherwise it moves to the next vehicle. */
    private int offerTimeoutSeconds = 45;
    /** A ride still unassigned after this long becomes UNFULFILLED and needs the control room. */
    private int searchTimeoutMinutes = 15;
    /** Scheduled rides start looking for a vehicle this many minutes before pickup time. */
    private int scheduledDispatchLeadMinutes = 15;
    private boolean exclusiveRideRequiresApproval = true;
    /** Optional module: lets hosts book rides for visitors against a gate pass reference. */
    private boolean visitorModuleEnabled = false;
    private boolean active = true;
}
