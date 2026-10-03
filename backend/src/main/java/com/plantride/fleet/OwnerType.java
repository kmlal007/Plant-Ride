package com.plantride.fleet;

public enum OwnerType {
    /** Owned by one department; serves that department only. */
    DEPARTMENT,
    /** Contract vehicle from a vendor; serves the whole plant. */
    VENDOR,
    /** Centrally owned pool vehicle; serves the whole plant. */
    POOL
}
