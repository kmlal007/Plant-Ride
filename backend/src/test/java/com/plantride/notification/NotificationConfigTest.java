package com.plantride.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotificationConfigTest {

    private final NotificationConfig config = new NotificationConfig();

    @Test
    void fcmWithoutCredentialsFallsBackToLoggingSoTheAppStillStarts() {
        var props = new NotificationProperties(
                new NotificationProperties.Push("fcm", new NotificationProperties.Fcm("", ""), null), null);
        assertThat(config.pushSender(props, RestClient.builder()).name()).isEqualTo("none");
        assertThat(config.smsSender(props, RestClient.builder()).name()).isEqualTo("none");
    }

    @Test
    void webhookPushPostsJsonWithAuthHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gateway.plant.local/push"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(content().json("{\"tokens\":[\"t1\"],\"title\":\"Hi\",\"body\":\"There\"}"))
                .andRespond(withSuccess());
        var props = new NotificationProperties(new NotificationProperties.Push("webhook", null,
                new NotificationProperties.Webhook("https://gateway.plant.local/push", "Bearer secret")), null);
        PushSender sender = config.pushSender(props, builder);
        assertThat(sender.name()).isEqualTo("webhook");
        sender.send(List.of("t1"), new PushMessage("Hi", "There", Map.of()));
        server.verify();
    }
}
