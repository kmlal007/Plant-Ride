package com.plantride.notification;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/** Chooses push and SMS channels from configuration, falling back to logging if misconfigured. */
@Configuration
@EnableAsync
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    public PushSender pushSender(NotificationProperties props, RestClient.Builder builder) {
        String provider = props.push() == null || props.push().provider() == null ? "fcm" : props.push().provider();
        switch (provider) {
            case "fcm" -> {
                NotificationProperties.Fcm fcm = props.push().fcm();
                if (fcm == null || isBlank(fcm.projectId()) || isBlank(fcm.credentialsFile())) {
                    log.warn("Push provider is fcm but FCM_PROJECT_ID / FCM_CREDENTIALS_FILE are not set: "
                            + "push disabled, apps fall back to polling");
                    return LoggingSenders.push();
                }
                try {
                    return new FcmPushSender(fcm.projectId(), fcm.credentialsFile(), builder);
                } catch (Exception e) {
                    log.error("Could not initialise FCM ({}); push disabled", e.getMessage());
                    return LoggingSenders.push();
                }
            }
            case "webhook" -> {
                NotificationProperties.Webhook w = props.push().webhook();
                if (w == null || isBlank(w.url())) {
                    log.warn("Push provider is webhook but no URL is configured: push disabled");
                    return LoggingSenders.push();
                }
                return WebhookSenders.push(w.url(), w.authHeader(), builder);
            }
            case "none" -> {
                return LoggingSenders.push();
            }
            default -> throw new IllegalStateException("Unknown push provider: " + provider);
        }
    }

    @Bean
    public SmsSender smsSender(NotificationProperties props, RestClient.Builder builder) {
        String provider = props.sms() == null || props.sms().provider() == null ? "none" : props.sms().provider();
        switch (provider) {
            case "webhook" -> {
                NotificationProperties.Webhook w = props.sms().webhook();
                if (w == null || isBlank(w.url())) {
                    log.warn("SMS provider is webhook but no URL is configured: SMS disabled");
                    return LoggingSenders.sms();
                }
                return WebhookSenders.sms(w.url(), w.authHeader(), builder);
            }
            case "none" -> {
                return LoggingSenders.sms();
            }
            default -> throw new IllegalStateException("Unknown SMS provider: " + provider);
        }
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("notify-");
        ex.initialize();
        return ex;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
