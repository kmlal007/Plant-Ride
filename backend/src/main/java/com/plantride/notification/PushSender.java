package com.plantride.notification;

import java.util.List;
import java.util.Set;

/** A push delivery channel. Implementations are chosen by {@code plantride.notifications.push.provider}. */
public interface PushSender {

    String name();

    /** Sends to the given device tokens; returns tokens the provider reports as permanently invalid. */
    Set<String> send(List<String> tokens, PushMessage message);
}
