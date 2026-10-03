package com.plantride.notification;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.google.auth.oauth2.GoogleCredentials;

/**
 * Firebase Cloud Messaging HTTP v1. Needs a service account JSON with the Firebase Messaging role and
 * outbound HTTPS to oauth2.googleapis.com and fcm.googleapis.com (also from on-prem installations).
 */
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final String projectId;
    private final GoogleCredentials credentials;
    private final RestClient http;

    FcmPushSender(String projectId, String credentialsFile, RestClient.Builder builder) throws IOException {
        this.projectId = projectId;
        try (InputStream in = new FileInputStream(credentialsFile)) {
            this.credentials = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
        }
        this.http = builder.baseUrl("https://fcm.googleapis.com").build();
    }

    @Override
    public String name() {
        return "fcm";
    }

    @Override
    public Set<String> send(List<String> tokens, PushMessage message) {
        Set<String> invalid = new HashSet<>();
        String accessToken;
        try {
            credentials.refreshIfExpired();
            accessToken = credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.error("FCM authentication failed: {}", e.getMessage());
            return invalid;
        }
        for (String token : tokens) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("token", token);
            msg.put("notification", Map.of("title", message.title(), "body", message.body()));
            msg.put("data", message.data());
            msg.put("android", Map.of("priority", "high"));
            try {
                http.post()
                        .uri("/v1/projects/{project}/messages:send", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", msg))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
                // UNREGISTERED (404) or INVALID_ARGUMENT for a malformed token (400): drop it.
                invalid.add(token);
            } catch (RestClientException e) {
                log.warn("FCM send failed: {}", e.getMessage());
            }
        }
        return invalid;
    }
}
