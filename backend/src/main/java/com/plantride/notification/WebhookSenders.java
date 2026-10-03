package com.plantride.notification;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Generic HTTP channels for on-premise gateways (an in-house push relay, MQTT bridge or an SMS
 * aggregator adapter). The backend POSTs JSON; the gateway owns delivery.
 */
final class WebhookSenders {

    private static final Logger log = LoggerFactory.getLogger(WebhookSenders.class);

    private WebhookSenders() {
    }

    static PushSender push(String url, String authHeader, RestClient.Builder builder) {
        RestClient http = client(builder, authHeader);
        return new PushSender() {
            @Override
            public String name() {
                return "webhook";
            }

            @Override
            public Set<String> send(List<String> tokens, PushMessage message) {
                try {
                    http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("tokens", tokens, "title", message.title(), "body", message.body(),
                                    "data", message.data()))
                            .retrieve().toBodilessEntity();
                } catch (RestClientException e) {
                    log.warn("Push webhook failed: {}", e.getMessage());
                }
                return Set.of();
            }
        };
    }

    static SmsSender sms(String url, String authHeader, RestClient.Builder builder) {
        RestClient http = client(builder, authHeader);
        return new SmsSender() {
            @Override
            public String name() {
                return "webhook";
            }

            @Override
            public void send(String phone, String message) {
                try {
                    http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("to", phone, "message", message))
                            .retrieve().toBodilessEntity();
                } catch (RestClientException e) {
                    log.warn("SMS webhook failed for {}: {}", Masking.phone(phone), e.getMessage());
                }
            }
        };
    }

    private static RestClient client(RestClient.Builder builder, String authHeader) {
        RestClient.Builder b = builder.clone();
        if (authHeader != null && !authHeader.isBlank()) {
            b.defaultHeader("Authorization", authHeader);
        }
        return b.build();
    }
}
