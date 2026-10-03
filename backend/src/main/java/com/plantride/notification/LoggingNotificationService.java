package com.plantride.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    @Override
    public void notifyUser(Long userId, String title, String message) {
        log.info("notify user={} title='{}' message='{}'", userId, title, message);
    }

    @Override
    public void sms(String phone, String message) {
        // Never log the full number; message may contain an OTP, so log only its length.
        String masked = phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
        log.info("sms to={} length={}", masked, message.length());
    }
}
