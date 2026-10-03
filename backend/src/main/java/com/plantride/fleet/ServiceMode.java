package com.plantride.fleet;

public enum ServiceMode {
    /** Runs a timetabled route (bus/shuttle); tracked only, not dispatched. */
    FIXED_ROUTE,
    /** Dispatched for on-demand rides through the driver app. */
    ON_DEMAND
}
