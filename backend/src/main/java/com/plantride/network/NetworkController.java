package com.plantride.network;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.security.CurrentUser;

/** Rider app: find stops and shuttle/bus timings. */
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkService networkService;
    private final StopRepository stops;

    public NetworkController(NetworkService networkService, StopRepository stops) {
        this.networkService = networkService;
        this.stops = stops;
    }

    @GetMapping("/stops")
    public List<Stop> allStops() {
        return stops.findByPlantIdAndActiveTrue(CurrentUser.get().plantId());
    }

    @GetMapping("/stops/nearby")
    public List<NetworkService.NearbyStop> nearby(@RequestParam double lat, @RequestParam double lng,
                                                  @RequestParam(defaultValue = "5") int limit) {
        return networkService.nearbyStops(CurrentUser.get().plantId(), lat, lng, Math.min(limit, 20));
    }

    @GetMapping("/stops/{stopId}/arrivals")
    public NetworkService.StopArrivals arrivals(@PathVariable Long stopId,
                                                @RequestParam(defaultValue = "5") int limit) {
        return networkService.arrivals(CurrentUser.get().plantId(), stopId, Math.min(limit, 20));
    }

    @GetMapping("/journeys")
    public List<NetworkService.JourneyOption> journeys(@RequestParam double fromLat, @RequestParam double fromLng,
                                                       @RequestParam Long toStopId,
                                                       @RequestParam(defaultValue = "3") int limit) {
        return networkService.journeys(CurrentUser.get().plantId(), fromLat, fromLng, toStopId, Math.min(limit, 10));
    }
}
