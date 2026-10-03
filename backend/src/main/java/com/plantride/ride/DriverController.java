package com.plantride.ride;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.fleet.DriverDutyService;
import com.plantride.fleet.Vehicle;
import com.plantride.security.AuthUser;
import com.plantride.security.CurrentUser;

/** Driver app: sign on/off a vehicle and work the ride lifecycle. */
@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private final DriverDutyService dutyService;
    private final RideService rideService;

    public DriverController(DriverDutyService dutyService, RideService rideService) {
        this.dutyService = dutyService;
        this.rideService = rideService;
    }

    /** One call for the app's home screen: duty vehicle (if any) and the current ride (if any). */
    @GetMapping("/status")
    public Map<String, Object> status() {
        AuthUser me = CurrentUser.get();
        Map<String, Object> result = new HashMap<>();
        result.put("vehicle", dutyService.currentVehicle(me.userId()));
        result.put("ride", rideService.currentForDriver(me));
        return result;
    }

    @GetMapping("/vehicles")
    public List<Vehicle> eligibleVehicles() {
        AuthUser me = CurrentUser.get();
        return dutyService.eligibleVehicles(me.userId(), me.plantId());
    }

    @PostMapping("/duty/start")
    public Vehicle startDuty(@RequestBody Map<String, Long> body) {
        AuthUser me = CurrentUser.get();
        return dutyService.startDuty(me.userId(), me.plantId(), body.get("vehicleId"));
    }

    @PostMapping("/duty/end")
    public void endDuty() {
        dutyService.endDuty(CurrentUser.get().userId());
    }

    @GetMapping("/rides/completed")
    public List<RideView> completed() {
        return rideService.completedForDriver(CurrentUser.get());
    }

    @PostMapping("/rides/{id}/accept")
    public RideView accept(@PathVariable Long id) {
        return rideService.accept(CurrentUser.get(), id);
    }

    @PostMapping("/rides/{id}/decline")
    public RideView decline(@PathVariable Long id) {
        return rideService.decline(CurrentUser.get(), id);
    }

    @PostMapping("/rides/{id}/arrive")
    public RideView arrive(@PathVariable Long id) {
        return rideService.arrive(CurrentUser.get(), id);
    }

    @PostMapping("/rides/{id}/start")
    public RideView start(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return rideService.start(CurrentUser.get(), id, body.get("otp"));
    }

    @PostMapping("/rides/{id}/complete")
    public RideView complete(@PathVariable Long id) {
        return rideService.complete(CurrentUser.get(), id);
    }

    @PostMapping("/rides/{id}/no-show")
    public RideView noShow(@PathVariable Long id) {
        return rideService.noShow(CurrentUser.get(), id);
    }
}
