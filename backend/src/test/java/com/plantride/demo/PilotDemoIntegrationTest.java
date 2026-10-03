package com.plantride.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

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

/** The pilot showcase plant seeds consistently and the simulator drives a full ride end to end. */
@SpringBootTest(properties = {"plantride.demo.seed=true", "plantride.demo.simulator=true",
        "plantride.demo.bot-driver-logins=9000000001,9000000002,9000000003,9000000005,9000000006"})
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@DirtiesContext
class PilotDemoIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    MutableClock clock;
    @Autowired
    DemoSimulator simulator;

    @Test
    void seededPlantSupportsTheWalkthroughAndBotsCompleteRides() throws Exception {
        ApiClient api = new ApiClient(mvc, om);
        String admin = api.login("demo.admin", "password");

        JsonNode report = api.get(admin, "/api/admin/reports/cost-centers?from=2026-09-01&to=2026-10-05", 200);
        assertThat(report.size()).isGreaterThan(5);
        assertThat(api.get(admin, "/api/admin/rides?from=2026-09-05&to=2026-10-05", 200).size()).isGreaterThan(150);
        assertThat(api.get(api.login("M100", "password"), "/api/rides/approvals", 200)).hasSize(1);

        // A General Office employee books a shared ride; a bot driver handles it completely.
        String neha = api.login("E301", "password");
        JsonNode ride = api.post(neha, "/api/rides", Map.of("rideType", "SHARED",
                "pickupLabel", "General Office", "pickupLat", 22.794, "pickupLng", 86.184,
                "dropLabel", "Blast Furnace", "dropLat", 22.796, "dropLng", 86.206), 200);
        long id = ride.get("id").asLong();
        assertThat(ride.get("status").asText()).isEqualTo("OFFERED");

        String status = "";
        for (int i = 0; i < 200 && !status.equals("COMPLETED"); i++) {
            clock.advance(Duration.ofSeconds(5));
            simulator.tick();
            status = api.get(neha, "/api/rides/" + id, 200).get("status").asText();
        }
        JsonNode done = api.get(neha, "/api/rides/" + id, 200);
        assertThat(status).isEqualTo("COMPLETED");
        assertThat(done.get("distanceSource").asText()).isEqualTo("GPS");
        assertThat(done.get("fare").asDouble()).isPositive();

        // Shuttles report live positions during service hours (10:00 IST).
        JsonNode stops = api.get(neha, "/api/network/stops", 200);
        long biggestStop = 0;
        for (JsonNode s : stops) {
            if (s.get("code").asText().equals("BF")) {
                biggestStop = s.get("id").asLong();
            }
        }
        JsonNode arrivals = api.get(neha, "/api/network/stops/" + biggestStop + "/arrivals", 200);
        assertThat(arrivals.get("live").size()).isPositive();
        assertThat(arrivals.get("scheduled").size()).isPositive();
    }
}
