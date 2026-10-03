package com.plantride.notification;

/**
 * Outbound notifications. The default implementation only logs; push (FCM) and SMS/WhatsApp
 * gateways plug in here without touching business code. Until push is wired, apps poll.
 */
public interface NotificationService {

    void notifyUser(Long userId, String title, String message);

    void sms(String phone, String message);
}
