package com.plantride.notification;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A push token of one app install (FCM registration token on Android). */
@Entity
@Table(name = "device_token")
@Getter
@Setter
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    /** android | ios */
    private String platform;
    /** user | driver */
    private String app;
    private String token;
    private Instant createdAt;
    private Instant lastSeenAt;
}
