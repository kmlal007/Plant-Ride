package com.plantride.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plantride.notifications.push.provider: fcm (default) | webhook | none
 * plantride.notifications.sms.provider:  none (default) | webhook
 */
@ConfigurationProperties(prefix = "plantride.notifications")
public record NotificationProperties(Push push, Sms sms) {

    public record Push(String provider, Fcm fcm, Webhook webhook) {
    }

    public record Sms(String provider, Webhook webhook) {
    }

    public record Fcm(String projectId, String credentialsFile) {
    }

    public record Webhook(String url, String authHeader) {
    }
}
