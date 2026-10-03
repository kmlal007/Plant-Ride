package com.plantride.billing;

/**
 * Cost allocation choices that Finance configures per plant. Kept as explicit enums (not a rules
 * engine) so every option is visible, tested and reportable.
 */
public final class CostPolicies {

    private CostPolicies() {
    }

    /** May a department-owned vehicle serve other departments? */
    public enum DepartmentVehicleSharing {
        /** Only the owning department can book it. */
        OWN_DEPARTMENT_ONLY,
        /** Owning department first; when idle it is lent to the plant pool. */
        LEND_WHEN_IDLE
    }

    /** A department riding in its own vehicle. */
    public enum OwnVehicleChargeMode {
        /** Charge the ride at rate card like any other (full usage visibility). */
        CHARGE,
        /** Record the ride at zero: the vehicle's cost already sits in the department budget. */
        NO_CHARGE
    }

    /** Another department riding in a lent department vehicle. */
    public enum LentVehicleChargeMode {
        /** Booker pays the fare and the owning department receives an equal credit. */
        CHARGE_BOOKER_CREDIT_OWNER,
        /** Booker pays; no credit to the owner. */
        CHARGE_BOOKER_ONLY,
        /** Free: lending is goodwill. */
        NO_CHARGE
    }

    /** How the fixed monthly cost of shuttle/commute routes is distributed. */
    public enum ShuttleCostAllocation {
        /** Kept as central transport overhead; not charged to departments. */
        NOT_ALLOCATED,
        /** All charged to one designated cost center. */
        CENTRAL_COST_CENTER,
        /** Split across cost centers in proportion to active employees (by default cost center). */
        HEADCOUNT
    }
}
