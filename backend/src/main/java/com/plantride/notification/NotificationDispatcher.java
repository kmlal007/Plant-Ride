package com.plantride.notification;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final DeviceTokenRepository tokens;
    private final PushSender push;
    private final SmsSender sms;

    public NotificationDispatcher(DeviceTokenRepository tokens, PushSender push, SmsSender sms) {
        this.tokens = tokens;
        this.push = push;
        this.sms = sms;
        log.info("Notifications: push={} sms={}", push.name(), sms.name());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserNotification(NotificationService.UserNotification n) {
        List<String> deviceTokens = tokens.findByUserId(n.userId()).stream().map(DeviceToken::getToken).toList();
        if (deviceTokens.isEmpty()) {
            log.debug("User {} has no registered device; '{}' not pushed", n.userId(), n.message().title());
            return;
        }
        Set<String> invalid = push.send(deviceTokens, n.message());
        if (!invalid.isEmpty()) {
            tokens.deleteByTokenIn(invalid);
            log.info("Removed {} invalid push tokens", invalid.size());
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSms(NotificationService.SmsNotification n) {
        sms.send(n.phone(), n.message());
    }
}
