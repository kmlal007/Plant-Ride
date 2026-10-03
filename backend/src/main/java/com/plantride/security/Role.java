package com.plantride.security;

public enum Role {
    /** Configures plants, master data, fleet, routes, rates; sees all rides and reports. */
    ADMIN,
    /** Transport control room: live map, manual dispatch and ride oversight. */
    DISPATCHER,
    /** Rider: books rides, sees shuttles, approves rides of reportees. */
    EMPLOYEE,
    /** Cab/shuttle driver using the driver app. */
    DRIVER
}
