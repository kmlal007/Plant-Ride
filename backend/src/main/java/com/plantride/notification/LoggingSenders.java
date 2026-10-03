package com.plantride.notification;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** No-op channels that only log; used when a provider is set to "none" or is not configured. */
final class LoggingSenders {

    private static final Logger log = LoggerFactory.getLogger(LoggingSenders.class);

    private LoggingSenders() {
    }

    static PushSender push() {
        return new PushSender() {
            @Override
            public String name() {
                return "none";
            }

            @Override
            public Set<String> send(List<String> tokens, PushMessage message) {
                log.info("push (not delivered) devices={} title='{}' body='{}'", tokens.size(), message.title(),
                        message.body());
                return Set.of();
            }
        };
    }

    static SmsSender sms() {
        return new SmsSender() {
            @Override
            public String name() {
                return "none";
            }

            @Override
            public void send(String phone, String message) {
                log.info("sms (not delivered) to={} length={}", Masking.phone(phone), message.length());
            }
        };
    }
}
