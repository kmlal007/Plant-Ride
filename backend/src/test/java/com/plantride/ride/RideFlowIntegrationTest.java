package com.plantride.ride;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantride.support.ApiClient;
import com.plantride.support.MutableClock;
import com.plantride.support.TestClockConfig;

/**
 * End-to-end ride lifecycle through the HTTP API: admin setup, GPS ingestion, booking, approval,
 * dispatch, driver actions, completion and cost center charging.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RideFlowIntegrationTest {

    // Reference point inside the (fictional) plant.
    static final double LAT = 22.7900;
    static final double LNG = 86.1900;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    MutableClock clock;
    @Autowired
    RideSweeper sweeper;

    ApiClient api;
    String admin;
    long deptBf;
    long deptSms;
    long ccBf;
    long ccSms;
    long managerId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfig.START);
        api = new ApiClient(mvc, om);
        admin = api.login("admin", "admin123");
        deptBf = api.post(admin, "/api/admin/departments", Map.of("code", "BF", "name", "Blast Furnace"), 200).get("id").asLong();
        deptSms = api.post(admin, "/api/admin/departments", Map.of("code", "SMS", "name", "Steel Melt Shop"), 200).get("id").asLong();
        ccBf = api.post(admin, "/api/admin/cost-centers", Map.of("code", "CC-BF", "name", "BF Ops", "departmentId", deptBf), 200).get("id").asLong();
        ccSms = api.post(admin, "/api/admin/cost-centers", Map.of("code", "CC-SMS", "name", "SMS Ops", "departmentId", deptSms), 200).get("id").asLong();
        managerId = createUser("M1", "EMPLOYEE", deptBf, ccBf, null);
        createUser("E1", "EMPLOYEE", deptBf, ccBf, managerId);
        createUser("E2", "EMPLOYEE", deptSms, ccSms, null);
        createUser("D1", "DRIVER", null, null, null);
        createUser("D2", "DRIVER", null, null, null);
        api.post(admin, "/api/admin/rate-cards", Map.of("vehicleType", "CAR", "rideType", "EXCLUSIVE",
                "baseFare", 50, "perKm", 14, "perMinute", 1, "minimumFare", 50), 200);
        api.post(admin, "/api/admin/rate-cards", Map.of("vehicleType", "CAR", "rideType", "SHARED",
                "baseFare", 20, "perKm", 7, "perMinute", 0.5, "minimumFare", 20), 200);
    }

    @Test
    void exclusiveRideFromApprovalToCostedCompletion() throws Exception {
        long car = createVehicle("JH05AA0001", "POOL", null, "DEV-1");
        gps("DEV-1", LAT + 0.002, LNG, 0);
        String driver = api.login("D1", "password");
        api.post(driver, "/api/driver/duty/start", Map.of("vehicleId", car), 200);

        String employee = api.login("E1", "password");
        JsonNode ride = api.post(employee, "/api/rides", rideBody("EXCLUSIVE", null), 200);
        long rideId = ride.get("id").asLong();
        assertThat(ride.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        String otp = ride.get("otp").asText();
        assertThat(otp).hasSize(4);

        // Only the manager can approve.
        String other = api.login("E2", "password");
        api.post(other, "/api/rides/" + rideId + "/approve", null, 403);
        String manager = api.login("M1", "password");
        assertThat(api.get(manager, "/api/rides/approvals", 200)).hasSize(1);
        JsonNode approved = api.post(manager, "/api/rides/" + rideId + "/approve", null, 200);
        assertThat(approved.get("status").asText()).isEqualTo("OFFERED");
        assertThat(approved.get("otp").isNull()).as("OTP is hidden from non-requesters").isTrue();

        JsonNode status = api.get(driver, "/api/driver/status", 200);
        assertThat(status.get("ride").get("id").asLong()).isEqualTo(rideId);
        assertThat(status.get("vehicle").get("status").asText()).isEqualTo("ASSIGNED");

        api.post(driver, "/api/driver/rides/" + rideId + "/accept", null, 200);
        api.post(driver, "/api/driver/rides/" + rideId + "/arrive", null, 200);
        api.post(driver, "/api/driver/rides/" + rideId + "/start", Map.of("otp", "x" + otp), 400);
        JsonNode started = api.post(driver, "/api/driver/rides/" + rideId + "/start", Map.of("otp", otp), 200);
        assertThat(started.get("status").asText()).isEqualTo("IN_PROGRESS");

        // Drive ~2.2 km north in 10 minutes, reporting every 2 minutes.
        for (int i = 1; i <= 5; i++) {
            clock.advance(Duration.ofMinutes(2));
            gps("DEV-1", LAT + 0.002 + i * 0.004, LNG, 25);
        }
        JsonNode done = api.post(driver, "/api/driver/rides/" + rideId + "/complete", null, 200);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("distanceSource").asText()).isEqualTo("GPS");
        assertThat(done.get("distanceKm").asDouble()).isBetween(2.1, 2.3);
        assertThat(done.get("durationMinutes").asInt()).isEqualTo(10);
        // 50 + 14 x 2.22 + 1 x 10 = 91.08
        assertThat(done.get("fare").asDouble()).isBetween(89.0, 93.0);

        JsonNode report = api.get(admin, "/api/admin/reports/cost-centers", 200);
        assertThat(report).hasSize(1);
        assertThat(report.get(0).get("code").asText()).isEqualTo("CC-BF");
        assertThat(report.get(0).get("amount").asDouble()).isEqualTo(done.get("fare").asDouble());

        assertThat(api.get(driver, "/api/driver/status", 200).get("vehicle").get("status").asText())
                .isEqualTo("AVAILABLE");
    }

    @Test
    void expiredOfferMovesToNextDriverAndSearchEventuallyTimesOut() throws Exception {
        long near = createVehicle("JH05AA0001", "POOL", null, "DEV-1");
        long far = createVehicle("JH05AA0002", "POOL", null, "DEV-2");
        gps("DEV-1", LAT + 0.001, LNG, 0);
        gps("DEV-2", LAT + 0.02, LNG, 0);
        String d1 = api.login("D1", "password");
        String d2 = api.login("D2", "password");
        api.post(d1, "/api/driver/duty/start", Map.of("vehicleId", near), 200);
        api.post(d2, "/api/driver/duty/start", Map.of("vehicleId", far), 200);

        // SHARED rides never need approval.
        String employee = api.login("E1", "password");
        long rideId = api.post(employee, "/api/rides", rideBody("SHARED", null), 200).get("id").asLong();
        assertThat(api.get(d1, "/api/driver/status", 200).get("ride").get("id").asLong()).isEqualTo(rideId);

        // Driver 1 ignores the offer; after the timeout the sweeper offers it to driver 2.
        clock.advance(Duration.ofSeconds(46));
        gps("DEV-1", LAT + 0.001, LNG, 0);
        gps("DEV-2", LAT + 0.02, LNG, 0);
        sweeper.run();
        assertThat(api.get(d1, "/api/driver/status", 200).get("ride").isNull()).isTrue();
        assertThat(api.get(d2, "/api/driver/status", 200).get("ride").get("id").asLong()).isEqualTo(rideId);
        api.post(d1, "/api/driver/rides/" + rideId + "/accept", null, 404);

        // Driver 2 declines too; nobody left -> stays SEARCHING, then UNFULFILLED after 15 minutes.
        api.post(d2, "/api/driver/rides/" + rideId + "/decline", null, 200);
        assertThat(api.get(employee, "/api/rides/" + rideId, 200).get("status").asText()).isEqualTo("SEARCHING");
        clock.advance(Duration.ofMinutes(16));
        sweeper.run();
        assertThat(api.get(employee, "/api/rides/" + rideId, 200).get("status").asText()).isEqualTo("UNFULFILLED");

        // Control room assigns manually; the vehicle goes back to driver 1.
        JsonNode assigned = api.post(admin, "/api/admin/rides/" + rideId + "/assign", Map.of("vehicleId", near), 200);
        assertThat(assigned.get("status").asText()).isEqualTo("OFFERED");
        assertThat(assigned.get("driverId").asLong()).isEqualTo(api.get(d1, "/api/me", 200).get("user").get("id").asLong());
    }

    @Test
    void departmentVehicleServesOnlyItsOwnDepartment() throws Exception {
        long bfCar = createVehicle("JH05BF0001", "DEPARTMENT", deptBf, "DEV-1");
        gps("DEV-1", LAT + 0.001, LNG, 0);
        api.post(api.login("D1", "password"), "/api/driver/duty/start", Map.of("vehicleId", bfCar), 200);

        String smsEmployee = api.login("E2", "password");
        JsonNode smsRide = api.post(smsEmployee, "/api/rides", rideBody("SHARED", null), 200);
        assertThat(smsRide.get("status").asText()).isEqualTo("SEARCHING");

        String bfEmployee = api.login("E1", "password");
        JsonNode bfRide = api.post(bfEmployee, "/api/rides", rideBody("SHARED", null), 200);
        assertThat(bfRide.get("status").asText()).isEqualTo("OFFERED");
        assertThat(bfRide.get("vehicleId").asLong()).isEqualTo(bfCar);
    }

    @Test
    void scheduledRideDispatchesShortlyBeforePickup() throws Exception {
        long car = createVehicle("JH05AA0001", "POOL", null, "DEV-1");
        api.post(api.login("D1", "password"), "/api/driver/duty/start", Map.of("vehicleId", car), 200);
        String employee = api.login("E2", "password");
        Instant pickup = clock.instant().plus(Duration.ofHours(1));
        long rideId = api.post(employee, "/api/rides", rideBody("EXCLUSIVE", pickup), 200).get("id").asLong();
        // E2 has no manager, so no approval step.
        assertThat(api.get(employee, "/api/rides/" + rideId, 200).get("status").asText()).isEqualTo("SCHEDULED");

        clock.advance(Duration.ofMinutes(30));
        sweeper.run();
        assertThat(api.get(employee, "/api/rides/" + rideId, 200).get("status").asText()).isEqualTo("SCHEDULED");

        clock.advance(Duration.ofMinutes(16));
        gps("DEV-1", LAT, LNG + 0.001, 0);
        sweeper.run();
        assertThat(api.get(employee, "/api/rides/" + rideId, 200).get("status").asText()).isEqualTo("OFFERED");
    }

    @Test
    void cancellingAnOfferedRideFreesTheVehicle() throws Exception {
        long car = createVehicle("JH05AA0001", "POOL", null, "DEV-1");
        gps("DEV-1", LAT, LNG + 0.001, 0);
        String driver = api.login("D1", "password");
        api.post(driver, "/api/driver/duty/start", Map.of("vehicleId", car), 200);
        String employee = api.login("E2", "password");
        long rideId = api.post(employee, "/api/rides", rideBody("SHARED", null), 200).get("id").asLong();

        api.post(api.login("E1", "password"), "/api/rides/" + rideId + "/cancel", null, 404);
        JsonNode cancelled = api.post(employee, "/api/rides/" + rideId + "/cancel", Map.of("reason", "Meeting moved"), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        JsonNode status = api.get(driver, "/api/driver/status", 200);
        assertThat(status.get("ride").isNull()).isTrue();
        assertThat(status.get("vehicle").get("status").asText()).isEqualTo("AVAILABLE");
        api.post(driver, "/api/driver/duty/end", null, 200);
    }

    @Test
    void visitorRidesRequireTheOptionalModule() throws Exception {
        String employee = api.login("E2", "password");
        Map<String, Object> body = rideBody("SHARED", null);
        body.put("visitorName", "Delegate One");
        body.put("visitorPhone", "9876543210");
        body.put("gatePassRef", "GP-123");
        api.post(employee, "/api/rides", body, 400);

        JsonNode plant = api.get(admin, "/api/admin/plants", 200).get(0);
        Map<String, Object> update = om.convertValue(plant, Map.class);
        update.put("visitorModuleEnabled", true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/admin/plants/" + plant.get("id").asLong())
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json").content(om.writeValueAsString(update)));

        JsonNode ride = api.post(employee, "/api/rides", body, 200);
        assertThat(ride.get("visitorName").asText()).isEqualTo("Delegate One");
        assertThat(ride.get("gatePassRef").asText()).isEqualTo("GP-123");
    }

    @Test
    void secondPlantRequiresMultiPlantMode() throws Exception {
        Map<String, Object> plant = Map.of("code", "PLANT2", "name", "Second Plant");
        api.post(admin, "/api/admin/plants", plant, 409);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/system-settings")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"multiPlantEnabled\":true}"));
        long plant2 = api.post(admin, "/api/admin/plants", plant, 200).get("id").asLong();

        // Admin switches plant: master data is scoped to the selected plant.
        String admin2 = api.post(admin, "/api/auth/switch-plant", Map.of("plantId", plant2), 200).get("token").asText();
        assertThat(api.get(admin2, "/api/admin/departments", 200)).isEmpty();
        assertThat(api.get(admin, "/api/admin/departments", 200)).hasSize(2);
        // Employees cannot use admin APIs.
        api.get(api.login("E1", "password"), "/api/admin/departments", 403);
    }

    @Test
    void trackingRejectsWrongApiKeyAndRaisesOverSpeedEvents() throws Exception {
        createVehicle("JH05AA0001", "POOL", null, "DEV-1");
        api.postWithHeader("/api/tracking/positions", List.of(), "X-Api-Key", "wrong", 401);
        gps("DEV-1", LAT, LNG, 45);
        clock.advance(Duration.ofSeconds(30));
        gps("DEV-1", LAT, LNG + 0.001, 50);
        JsonNode events = api.get(admin, "/api/admin/safety-events", 200);
        assertThat(events).as("debounced to one event").hasSize(1);
        assertThat(events.get(0).get("eventType").asText()).isEqualTo("OVER_SPEED");

        // Traccar forward format reports speed in knots: 10 knots = 18.5 km/h, under the limit.
        Map<String, Object> traccar = Map.of(
                "position", Map.of("latitude", LAT, "longitude", LNG, "speed", 10, "fixTime", clock.instant().plusSeconds(5).toString()),
                "device", Map.of("uniqueId", "DEV-1"));
        api.postWithHeader("/api/tracking/traccar", traccar, "X-Api-Key", "test-key", 200);
        assertThat(api.get(admin, "/api/admin/vehicles", 200).get(0).get("lastSpeedKmh").asDouble()).isBetween(18.0, 19.0);
    }

    // ------------------------------------------------------------------ helpers

    private long createUser(String login, String role, Long deptId, Long ccId, Long managerId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("loginId", login);
        body.put("name", login + " Name");
        body.put("phone", "90000000" + login.substring(1));
        body.put("password", "password");
        body.put("role", role);
        body.put("departmentId", deptId);
        body.put("defaultCostCenterId", ccId);
        body.put("managerId", managerId);
        return api.post(admin, "/api/admin/users", body, 200).get("id").asLong();
    }

    private long createVehicle(String reg, String ownerType, Long deptId, String device) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("registrationNo", reg);
        body.put("vehicleType", "CAR");
        body.put("capacity", 4);
        body.put("ownerType", ownerType);
        body.put("ownerDepartmentId", deptId);
        body.put("serviceMode", "ON_DEMAND");
        body.put("gpsDeviceId", device);
        body.put("active", true);
        return api.post(admin, "/api/admin/vehicles", body, 200).get("id").asLong();
    }

    private void gps(String device, double lat, double lng, double speedKmh) throws Exception {
        api.postWithHeader("/api/tracking/positions",
                List.of(Map.of("deviceId", device, "lat", lat, "lng", lng, "speedKmh", speedKmh,
                        "fixTime", clock.instant().toString())),
                "X-Api-Key", "test-key", 200);
    }

    private Map<String, Object> rideBody(String rideType, Instant scheduledAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("rideType", rideType);
        body.put("pickupLabel", "General Office");
        body.put("pickupLat", LAT);
        body.put("pickupLng", LNG);
        body.put("dropLabel", "Blast Furnace");
        body.put("dropLat", LAT + 0.02);
        body.put("dropLng", LNG);
        body.put("purpose", "Shift review meeting");
        if (scheduledAt != null) {
            body.put("scheduledAt", scheduledAt.toString());
        }
        return body;
    }
}
