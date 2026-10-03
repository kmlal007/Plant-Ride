package com.plantride.notification;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Entry point for business code. Notifications are published as events and delivered asynchronously
 * after the surrounding transaction commits, so a slow or failing provider never blocks or rolls back
 * a ride action, and nobody is notified about a change that was rolled back.
 */
@Service
public class NotificationService {

    public record UserNotification(Long userId, PushMessage message) {
    }

    public record SmsNotification(String phone, String message) {
    }

    private final ApplicationEventPublisher events;

    public NotificationService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void notifyUser(Long userId, String title, String message) {
        notifyUser(userId, title, message, Map.of());
    }

    public void notifyUser(Long userId, String title, String message, Map<String, String> data) {
        if (userId != null) {
            events.publishEvent(new UserNotification(userId, new PushMessage(title, message, data)));
        }
    }

    public void sms(String phone, String message) {
        if (phone != null && !phone.isBlank()) {
            events.publishEvent(new SmsNotification(phone, message));
        }
    }
}
