package com.plantride.ride;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.fleet.VehicleStatus;
import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.plant.PlantService;
import com.plantride.security.AuthUser;
import com.plantride.security.CurrentUser;
import com.plantride.tracking.SafetyEventRepository;

/** Control room and admin: ride oversight, manual dispatch, dashboard and cost reports. */
@RestController
@RequestMapping("/api/admin")
public class RideAdminController {

    public record CostCenterReportRow(Long costCenterId, String code, String name, long rides, BigDecimal amount,
                                      BigDecimal monthlyBudget) {
    }

    private final RideService rideService;
    private final CostAllocationRepository allocations;
    private final CostCenterRepository costCenters;
    private final VehicleRepository vehicles;
    private final SafetyEventRepository safetyEvents;
    private final PlantService plantService;
    private final Clock clock;

    public RideAdminController(RideService rideService, CostAllocationRepository allocations,
                               CostCenterRepository costCenters, VehicleRepository vehicles,
                               SafetyEventRepository safetyEvents, PlantService plantService, Clock clock) {
        this.rideService = rideService;
        this.allocations = allocations;
        this.costCenters = costCenters;
        this.vehicles = vehicles;
        this.safetyEvents = safetyEvents;
        this.plantService = plantService;
        this.clock = clock;
    }

    /** Rides created between two plant-local dates (inclusive); defaults to today. */
    @GetMapping("/rides")
    public List<RideView> rides(@RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to,
                                @RequestParam(required = false) RideStatus status) {
        AuthUser me = CurrentUser.get();
        Instant[] range = range(me.plantId(), from, to);
        return rideService.search(me.plantId(), range[0], range[1], status);
    }

    @PostMapping("/rides/{id}/assign")
    public RideView assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        if (body.get("vehicleId") == null) {
            throw ApiException.badRequest("vehicleId is required");
        }
        return rideService.assignManually(CurrentUser.get().plantId(), id, body.get("vehicleId"));
    }

    @PostMapping("/rides/{id}/cancel")
    public RideView cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return rideService.cancel(CurrentUser.get(), id, body == null ? null : body.get("reason"));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        AuthUser me = CurrentUser.get();
        Instant[] today = range(me.plantId(), null, null);
        Map<RideStatus, Long> rideCounts = new EnumMap<>(RideStatus.class);
        Arrays.stream(RideStatus.values()).forEach(s -> rideCounts.put(s, 0L));
        rideService.search(me.plantId(), today[0], today[1], null)
                .forEach(r -> rideCounts.merge(r.status(), 1L, Long::sum));
        Map<VehicleStatus, Long> vehicleCounts = vehicles.findByPlantIdOrderByIdAsc(me.plantId()).stream()
                .filter(Vehicle::isActive)
                .collect(Collectors.groupingBy(Vehicle::getStatus, () -> new EnumMap<>(VehicleStatus.class),
                        Collectors.counting()));
        long openSafety = safetyEvents.findTop100ByPlantIdOrderByOccurredAtDesc(me.plantId()).stream()
                .filter(e -> !e.isAcknowledged()).count();
        return Map.of("ridesToday", rideCounts, "vehicles", vehicleCounts, "openSafetyEvents", openSafety);
    }

    @GetMapping("/reports/cost-centers")
    public List<CostCenterReportRow> costCenterReport(@RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to) {
        AuthUser me = CurrentUser.get();
        Instant[] range = range(me.plantId(), from, to);
        Map<Long, CostCenter> ccById = costCenters.findByPlantIdOrderByIdAsc(me.plantId()).stream()
                .collect(Collectors.toMap(CostCenter::getId, Function.identity()));
        return allocations.totalsByCostCenter(me.plantId(), range[0], range[1]).stream()
                .map(t -> {
                    CostCenter cc = ccById.get(t.costCenterId());
                    return new CostCenterReportRow(t.costCenterId(), cc == null ? null : cc.getCode(),
                            cc == null ? null : cc.getName(), t.rides(), t.amount(),
                            cc == null ? null : cc.getMonthlyBudget());
                })
                .sorted((a, b) -> b.amount().compareTo(a.amount()))
                .toList();
    }

    private Instant[] range(Long plantId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(plantService.require(plantId).getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        if (end.isAfter(start.plusDays(366))) {
            throw ApiException.badRequest("Date range is limited to one year");
        }
        return new Instant[] {start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant()};
    }
}
