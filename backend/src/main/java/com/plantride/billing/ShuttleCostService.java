package com.plantride.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.billing.CostPolicies.ShuttleCostAllocation;
import com.plantride.common.ApiException;
import com.plantride.network.Route;
import com.plantride.network.RouteRepository;
import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantService;
import com.plantride.ride.CostAllocation;
import com.plantride.ride.CostAllocationRepository;
import com.plantride.security.Role;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Distributes the fixed monthly cost of active routes to cost centers per the plant's policy.
 * A month is previewed first and then posted once; posted rows are immutable.
 */
@Service
public class ShuttleCostService {

    static final List<String> SHUTTLE_BASES =
            List.of(CostAllocation.BASIS_SHUTTLE_CENTRAL, CostAllocation.BASIS_SHUTTLE_HEADCOUNT);

    public record Line(Long costCenterId, String code, String name, long headcount, BigDecimal amount) {
    }

    public record Preview(String period, ShuttleCostAllocation policy, BigDecimal totalRouteCost, boolean posted,
                          List<Line> lines) {
    }

    private final RouteRepository routes;
    private final CostCenterRepository costCenters;
    private final AppUserRepository users;
    private final CostAllocationRepository allocations;
    private final PlantService plantService;
    private final Clock clock;

    public ShuttleCostService(RouteRepository routes, CostCenterRepository costCenters, AppUserRepository users,
                              CostAllocationRepository allocations, PlantService plantService, Clock clock) {
        this.routes = routes;
        this.costCenters = costCenters;
        this.users = users;
        this.allocations = allocations;
        this.plantService = plantService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preview preview(Long plantId, YearMonth month) {
        Plant plant = plantService.require(plantId);
        BigDecimal total = routes.findByPlantIdAndActiveTrue(plantId).stream()
                .map(Route::getMonthlyCost).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean posted = allocations.existsByPlantIdAndPeriodAndBasisIn(plantId, month.toString(), SHUTTLE_BASES);
        Map<Long, CostCenter> ccById = costCenters.findByPlantIdOrderByIdAsc(plantId).stream()
                .collect(Collectors.toMap(CostCenter::getId, Function.identity()));

        List<Line> lines = switch (plant.getShuttleCostAllocation()) {
            case NOT_ALLOCATED -> List.of();
            case CENTRAL_COST_CENTER -> {
                CostCenter cc = ccById.get(plant.getShuttleCentralCostCenterId());
                if (cc == null) {
                    throw ApiException.badRequest("Central cost center for shuttle costs is not configured");
                }
                yield List.of(new Line(cc.getId(), cc.getCode(), cc.getName(), 0, total));
            }
            case HEADCOUNT -> byHeadcount(plantId, total, ccById);
        };
        return new Preview(month.toString(), plant.getShuttleCostAllocation(), total, posted, lines);
    }

    @Transactional
    public Preview post(Long plantId, YearMonth month) {
        if (!month.isBefore(YearMonth.now(clock).plusMonths(1))) {
            throw ApiException.badRequest("Cannot post a future month");
        }
        Preview preview = preview(plantId, month);
        if (preview.posted()) {
            throw ApiException.conflict("Shuttle costs for " + month + " are already posted");
        }
        if (preview.lines().isEmpty()) {
            throw ApiException.badRequest("Nothing to post: policy is NOT_ALLOCATED or no route has a monthly cost");
        }
        ZoneId zone = ZoneId.of(plantService.require(plantId).getTimezone());
        Instant at = month.atDay(1).atStartOfDay(zone).toInstant();
        String basis = preview.policy() == ShuttleCostAllocation.HEADCOUNT
                ? CostAllocation.BASIS_SHUTTLE_HEADCOUNT : CostAllocation.BASIS_SHUTTLE_CENTRAL;
        for (Line line : preview.lines()) {
            CostAllocation a = new CostAllocation();
            a.setPlantId(plantId);
            a.setCostCenterId(line.costCenterId());
            a.setAmount(line.amount());
            a.setBasis(basis);
            a.setPeriod(month.toString());
            a.setAllocatedAt(at);
            allocations.save(a);
        }
        return new Preview(preview.period(), preview.policy(), preview.totalRouteCost(), true, preview.lines());
    }

    /** Pro-rata by active employees; the rounding remainder goes to the largest cost center. */
    private List<Line> byHeadcount(Long plantId, BigDecimal total, Map<Long, CostCenter> ccById) {
        Map<Long, Long> heads = users.findByPlantIdOrderByIdAsc(plantId).stream()
                .filter(u -> u.isActive() && u.getRole() == Role.EMPLOYEE && u.getDefaultCostCenterId() != null)
                .filter(u -> ccById.containsKey(u.getDefaultCostCenterId()))
                .collect(Collectors.groupingBy(AppUser::getDefaultCostCenterId, Collectors.counting()));
        long totalHeads = heads.values().stream().mapToLong(Long::longValue).sum();
        if (totalHeads == 0 || total.signum() == 0) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (Map.Entry<Long, Long> e : heads.entrySet()) {
            BigDecimal share = total.multiply(BigDecimal.valueOf(e.getValue()))
                    .divide(BigDecimal.valueOf(totalHeads), 2, RoundingMode.DOWN);
            assigned = assigned.add(share);
            CostCenter cc = ccById.get(e.getKey());
            lines.add(new Line(cc.getId(), cc.getCode(), cc.getName(), e.getValue(), share));
        }
        lines.sort(Comparator.comparingLong(Line::headcount).reversed().thenComparing(Line::code));
        BigDecimal remainder = total.subtract(assigned);
        if (remainder.signum() != 0) {
            Line first = lines.get(0);
            lines.set(0, new Line(first.costCenterId(), first.code(), first.name(), first.headcount(),
                    first.amount().add(remainder)));
        }
        return lines;
    }
}
