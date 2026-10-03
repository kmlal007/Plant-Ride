package com.plantride.ride;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantride.notification.DeviceTokenRepository;
import com.plantride.support.ApiClient;
import com.plantride.support.MutableClock;
import com.plantride.support.TestClockConfig;

/** Configurable cost policies: department vehicle lending/credits and shuttle cost allocation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CostPolicyIntegrationTest {

    static final double LAT = 22.79;
    static final double LNG = 86.19;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    MutableClock clock;
    @Autowired
    CostAllocationRepository allocations;
    @Autowired
    DeviceTokenRepository deviceTokens;

    ApiClient api;
    String admin;
    long deptBf;
    long ccBf;
    long ccSms;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(TestClockConfig.START);
        api = new ApiClient(mvc, om);
        admin = api.login("admin", "admin123");
        deptBf = api.post(admin, "/api/admin/departments", Map.of("code", "BF", "name", "Blast Furnace"), 200).get("id").asLong();
        long deptSms = api.post(admin, "/api/admin/departments", Map.of("code", "SMS", "name", "SMS"), 200).get("id").asLong();
        ccBf = api.post(admin, "/api/admin/cost-centers", Map.of("code", "CC-BF", "name", "BF", "departmentId", deptBf), 200).get("id").asLong();
        ccSms = api.post(admin, "/api/admin/cost-centers", Map.of("code", "CC-SMS", "name", "SMS", "departmentId", deptSms), 200).get("id").asLong();
        user("E-BF", "EMPLOYEE", deptBf, ccBf);
        user("E-SMS1", "EMPLOYEE", deptSms, ccSms);
        user("E-SMS2", "EMPLOYEE", deptSms, ccSms);
        user("E-SMS3", "EMPLOYEE", deptSms, ccSms);
        user("D1", "DRIVER", null, null);
        api.post(admin, "/api/admin/rate-cards", Map.of("vehicleType", "CAR", "rideType", "SHARED",
                "baseFare", 100, "perKm", 0, "perMinute", 0, "minimumFare", 100), 200);
    }

    @Test
    void lentDepartmentVehicleChargesBookerAndCreditsOwner() throws Exception {
        setPlantPolicy(Map.of("departmentVehicleSharing", "LEND_WHEN_IDLE",
                "lentVehicleChargeMode", "CHARGE_BOOKER_CREDIT_OWNER"));
        long bfCar = vehicle("DEPARTMENT", deptBf, "DEV-BF");
        completeRideAs("E-SMS1", bfCar, "DEV-BF");

        Map<Long, Double> byCc = new HashMap<>();
        allocations.findAll().forEach(a -> byCc.merge(a.getCostCenterId(), a.getAmount().doubleValue(), Double::sum));
        assertThat(byCc).containsEntry(ccSms, 100.0).containsEntry(ccBf, -100.0);
    }

    @Test
    void ownVehicleCanBeConfiguredAsNoCharge() throws Exception {
        setPlantPolicy(Map.of("ownVehicleChargeMode", "NO_CHARGE"));
        long bfCar = vehicle("DEPARTMENT", deptBf, "DEV-BF");
        JsonNode done = completeRideAs("E-BF", bfCar, "DEV-BF");
        assertThat(done.get("fare").asDouble()).as("fare is still recorded").isEqualTo(100.0);
        assertThat(allocations.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getAmount().doubleValue()).isZero());
    }

    @Test
    void departmentVehicleIsNotLentByDefault() throws Exception {
        long bfCar = vehicle("DEPARTMENT", deptBf, "DEV-BF");
        gps("DEV-BF");
        api.post(api.login("D1", "password"), "/api/driver/duty/start", Map.of("vehicleId", bfCar), 200);
        JsonNode ride = api.post(api.login("E-SMS1", "password"), "/api/rides", rideBody(), 200);
        assertThat(ride.get("status").asText()).isEqualTo("SEARCHING");
    }

    @Test
    void shuttleCostsSplitByHeadcountAndPostOnce() throws Exception {
        long s1 = api.post(admin, "/api/admin/stops", Map.of("code", "A", "name", "A", "lat", LAT, "lng", LNG), 200).get("id").asLong();
        long s2 = api.post(admin, "/api/admin/stops", Map.of("code", "B", "name", "B", "lat", LAT + 0.01, "lng", LNG), 200).get("id").asLong();
        Map<String, Object> route = new HashMap<>(Map.of("code", "S1", "name", "Loop", "routeKind", "SHUTTLE",
                "firstDeparture", "06:00", "lastDeparture", "22:00", "headwayMinutes", 15,
                "stops", List.of(Map.of("stopId", s1, "offsetMinutes", 0), Map.of("stopId", s2, "offsetMinutes", 5))));
        route.put("monthlyCost", 1000);
        api.post(admin, "/api/admin/routes", route, 200);

        String month = YearMonth.from(clock.instant().atZone(java.time.ZoneOffset.UTC)).minusMonths(1).toString();
        // Default policy: not allocated.
        assertThat(api.get(admin, "/api/admin/shuttle-costs?month=" + month, 200).get("lines")).isEmpty();

        setPlantPolicy(Map.of("shuttleCostAllocation", "HEADCOUNT"));
        JsonNode preview = api.get(admin, "/api/admin/shuttle-costs?month=" + month, 200);
        assertThat(preview.get("totalRouteCost").asDouble()).isEqualTo(1000.0);
        // 1 BF employee vs 3 SMS employees.
        Map<String, Double> lines = new HashMap<>();
        preview.get("lines").forEach(l -> lines.put(l.get("code").asText(), l.get("amount").asDouble()));
        assertThat(lines).containsEntry("CC-SMS", 750.0).containsEntry("CC-BF", 250.0);

        api.post(admin, "/api/admin/shuttle-costs/post?month=" + month, null, 200);
        api.post(admin, "/api/admin/shuttle-costs/post?month=" + month, null, 409);
        String future = YearMonth.from(clock.instant().atZone(java.time.ZoneOffset.UTC)).plusMonths(2).toString();
        api.post(admin, "/api/admin/shuttle-costs/post?month=" + future, null, 400);

        JsonNode report = api.get(admin, "/api/admin/reports/cost-centers?from=" + month + "-01&to=" + month + "-28", 200);
        assertThat(report).hasSize(2);
        assertThat(report.get(0).get("rides").asLong()).as("shuttle allocations are not rides").isZero();
    }

    @Test
    void centralShuttlePolicyNeedsACostCenter() throws Exception {
        setPlantPolicy(Map.of("shuttleCostAllocation", "CENTRAL_COST_CENTER"), 400);
    }

    @Test
    void devicesRegisterAndMoveBetweenUsers() throws Exception {
        String e1 = api.login("E-BF", "password");
        String e2 = api.login("E-SMS1", "password");
        Map<String, Object> body = Map.of("token", "fcm-token-1", "platform", "android", "app", "user");
        api.post(e1, "/api/me/devices", body, 200);
        api.post(e1, "/api/me/devices", Map.of("token", "x", "platform", "symbian", "app", "user"), 400);
        api.post(e2, "/api/me/devices", body, 200);
        assertThat(deviceTokens.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getUserId()).isNotNull());
        api.post(e2, "/api/me/devices/unregister", Map.of("token", "fcm-token-1"), 200);
        assertThat(deviceTokens.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode completeRideAs(String employeeLogin, long vehicleId, String device) throws Exception {
        gps(device);
        String driver = api.login("D1", "password");
        api.post(driver, "/api/driver/duty/start", Map.of("vehicleId", vehicleId), 200);
        JsonNode ride = api.post(api.login(employeeLogin, "password"), "/api/rides", rideBody(), 200);
        assertThat(ride.get("status").asText()).isEqualTo("OFFERED");
        long id = ride.get("id").asLong();
        String otp = ride.get("otp").asText();
        api.post(driver, "/api/driver/rides/" + id + "/accept", null, 200);
        api.post(driver, "/api/driver/rides/" + id + "/arrive", null, 200);
        api.post(driver, "/api/driver/rides/" + id + "/start", Map.of("otp", otp), 200);
        clock.advance(Duration.ofMinutes(5));
        return api.post(driver, "/api/driver/rides/" + id + "/complete", null, 200);
    }

    private void setPlantPolicy(Map<String, Object> changes) throws Exception {
        setPlantPolicy(changes, 200);
    }

    @SuppressWarnings("unchecked")
    private void setPlantPolicy(Map<String, Object> changes, int expected) throws Exception {
        JsonNode plant = api.get(admin, "/api/admin/plants", 200).get(0);
        Map<String, Object> body = om.convertValue(plant, Map.class);
        body.putAll(changes);
        int status = mvc.perform(MockMvcRequestBuilders.put("/api/admin/plants/" + plant.get("id").asLong())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(expected);
    }

    private void user(String login, String role, Long deptId, Long ccId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("loginId", login);
        body.put("name", login);
        body.put("password", "password");
        body.put("role", role);
        body.put("departmentId", deptId);
        body.put("defaultCostCenterId", ccId);
        api.post(admin, "/api/admin/users", body, 200);
    }

    private long vehicle(String ownerType, Long deptId, String device) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("registrationNo", device);
        body.put("vehicleType", "CAR");
        body.put("capacity", 4);
        body.put("ownerType", ownerType);
        body.put("ownerDepartmentId", deptId);
        body.put("serviceMode", "ON_DEMAND");
        body.put("gpsDeviceId", device);
        body.put("active", true);
        return api.post(admin, "/api/admin/vehicles", body, 200).get("id").asLong();
    }

    private void gps(String device) throws Exception {
        api.postWithHeader("/api/tracking/positions",
                List.of(Map.of("deviceId", device, "lat", LAT, "lng", LNG + 0.001, "speedKmh", 0,
                        "fixTime", clock.instant().toString())),
                "X-Api-Key", "test-key", 200);
    }

    private Map<String, Object> rideBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("rideType", "SHARED");
        body.put("pickupLabel", "Gate");
        body.put("pickupLat", LAT);
        body.put("pickupLng", LNG);
        body.put("dropLabel", "Office");
        body.put("dropLat", LAT + 0.02);
        body.put("dropLng", LNG);
        return body;
    }
}
