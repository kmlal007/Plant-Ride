package com.plantride.notification;

/** An SMS delivery channel. Implementations are chosen by {@code plantride.notifications.sms.provider}. */
public interface SmsSender {

    String name();

    void send(String phone, String message);
}
