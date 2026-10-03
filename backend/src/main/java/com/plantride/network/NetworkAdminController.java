package com.plantride.network;

import java.time.LocalTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.common.ApiException;
import com.plantride.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class NetworkAdminController {

    public record RouteStopRequest(@NotNull Long stopId, int offsetMinutes) {
    }

    public record RouteRequest(@NotBlank String code, @NotBlank String name, @NotNull RouteKind routeKind,
                               @NotNull LocalTime firstDeparture, @NotNull LocalTime lastDeparture,
                               int headwayMinutes, String daysOfWeek, Boolean active,
                               @NotEmpty List<RouteStopRequest> stops) {
    }

    public record RouteView(Route route, List<RouteStop> stops) {
    }

    private final StopRepository stops;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;

    public NetworkAdminController(StopRepository stops, RouteRepository routes, RouteStopRepository routeStops) {
        this.stops = stops;
        this.routes = routes;
        this.routeStops = routeStops;
    }

    @GetMapping("/stops")
    public List<Stop> listStops() {
        return stops.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId());
    }

    @PostMapping("/stops")
    public Stop createStop(@RequestBody Stop body) {
        validateStop(body);
        body.setId(null);
        body.setPlantId(CurrentUser.get().plantId());
        return stops.save(body);
    }

    @PutMapping("/stops/{id}")
    public Stop updateStop(@PathVariable Long id, @RequestBody Stop body) {
        validateStop(body);
        Stop s = stops.require(id, CurrentUser.get().plantId(), "Stop");
        s.setCode(body.getCode());
        s.setName(body.getName());
        s.setLat(body.getLat());
        s.setLng(body.getLng());
        s.setActive(body.isActive());
        return s;
    }

    @GetMapping("/routes")
    public List<RouteView> listRoutes() {
        return routes.findByPlantIdOrderByIdAsc(CurrentUser.get().plantId()).stream()
                .map(r -> new RouteView(r, routeStops.findByRouteIdOrderBySeqAsc(r.getId())))
                .toList();
    }

    @PostMapping("/routes")
    public RouteView createRoute(@Valid @RequestBody RouteRequest req) {
        Route route = new Route();
        route.setPlantId(CurrentUser.get().plantId());
        return saveRoute(route, req);
    }

    @PutMapping("/routes/{id}")
    public RouteView updateRoute(@PathVariable Long id, @Valid @RequestBody RouteRequest req) {
        Route route = routes.require(id, CurrentUser.get().plantId(), "Route");
        return saveRoute(route, req);
    }

    private RouteView saveRoute(Route route, RouteRequest req) {
        if (req.headwayMinutes() < 1) {
            throw ApiException.badRequest("headwayMinutes must be at least 1");
        }
        if (req.lastDeparture().isBefore(req.firstDeparture())) {
            throw ApiException.badRequest("lastDeparture must not be before firstDeparture");
        }
        if (req.stops().size() < 2) {
            throw ApiException.badRequest("A route needs at least two stops");
        }
        int previousOffset = -1;
        for (RouteStopRequest rs : req.stops()) {
            stops.require(rs.stopId(), route.getPlantId(), "Stop");
            if (rs.offsetMinutes() < 0 || rs.offsetMinutes() < previousOffset) {
                throw ApiException.badRequest("Stop offsets must start at 0 and never decrease");
            }
            previousOffset = rs.offsetMinutes();
        }
        try {
            ScheduleCalculator.parseDays(req.daysOfWeek());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("daysOfWeek must be comma separated numbers 1 (Mon) to 7 (Sun)");
        }
        route.setCode(req.code());
        route.setName(req.name());
        route.setRouteKind(req.routeKind());
        route.setFirstDeparture(req.firstDeparture());
        route.setLastDeparture(req.lastDeparture());
        route.setHeadwayMinutes(req.headwayMinutes());
        route.setDaysOfWeek(req.daysOfWeek() == null || req.daysOfWeek().isBlank() ? "1,2,3,4,5,6,7" : req.daysOfWeek());
        route.setActive(req.active() == null || req.active());
        Route saved = routes.save(route);

        routeStops.deleteByRouteId(saved.getId());
        routeStops.flush();
        int seq = 1;
        for (RouteStopRequest rs : req.stops()) {
            RouteStop entity = new RouteStop();
            entity.setRouteId(saved.getId());
            entity.setSeq(seq++);
            entity.setStopId(rs.stopId());
            entity.setOffsetMinutes(rs.offsetMinutes());
            routeStops.save(entity);
        }
        return new RouteView(saved, routeStops.findByRouteIdOrderBySeqAsc(saved.getId()));
    }

    private static void validateStop(Stop s) {
        if (s.getCode() == null || s.getCode().isBlank() || s.getName() == null || s.getName().isBlank()) {
            throw ApiException.badRequest("code and name are required");
        }
        if (Math.abs(s.getLat()) > 90 || Math.abs(s.getLng()) > 180 || (s.getLat() == 0 && s.getLng() == 0)) {
            throw ApiException.badRequest("Valid lat/lng are required");
        }
    }
}
