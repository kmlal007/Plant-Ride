package com.plantride.ride;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.security.CurrentUser;

/** Rider app: book, track and cancel rides; approvers act on pending requests. */
@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public RideView create(@RequestBody RideService.CreateRideRequest req) {
        return rideService.create(CurrentUser.get(), req);
    }

    @GetMapping("/mine")
    public List<RideView> mine() {
        return rideService.myRides(CurrentUser.get());
    }

    @GetMapping("/{id}")
    public RideView get(@PathVariable Long id) {
        return rideService.get(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/cancel")
    public RideView cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return rideService.cancel(CurrentUser.get(), id, body == null ? null : body.get("reason"));
    }

    @GetMapping("/approvals")
    public List<RideView> approvals() {
        return rideService.pendingApprovals(CurrentUser.get());
    }

    @PostMapping("/{id}/approve")
    public RideView approve(@PathVariable Long id) {
        return rideService.decide(CurrentUser.get(), id, true, null);
    }

    @PostMapping("/{id}/reject")
    public RideView reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return rideService.decide(CurrentUser.get(), id, false, body == null ? null : body.get("reason"));
    }
}
