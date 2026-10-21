package com.festpass.realtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Counts "people viewing now" for each event: the number of WebSocket connections subscribed
 * to at least one of the event's topics. Spring publishes the subscribe event only after the
 * StompGuard accepted the subscription.
 */
@Component
public class ViewerTracker {

    /** connection id -> (subscription id -> event id) */
    private final Map<String, Map<String, Long>> subscriptions = new ConcurrentHashMap<>();
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor stomp = StompHeaderAccessor.wrap(event.getMessage());
        Matcher topic = StompGuard.EVENT_TOPIC.matcher(String.valueOf(stomp.getDestination()));
        if (topic.matches() && stomp.getSessionId() != null && stomp.getSubscriptionId() != null) {
            long eventId = Long.parseLong(topic.group(1));
            subscriptions.computeIfAbsent(stomp.getSessionId(), id -> new ConcurrentHashMap<>())
                    .put(stomp.getSubscriptionId(), eventId);
            changed.add(eventId);
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor stomp = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Long> subs = stomp.getSessionId() == null ? null : subscriptions.get(stomp.getSessionId());
        if (subs != null && stomp.getSubscriptionId() != null) {
            Long eventId = subs.remove(stomp.getSubscriptionId());
            if (eventId != null) {
                changed.add(eventId);
            }
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Map<String, Long> subs = subscriptions.remove(event.getSessionId());
        if (subs != null) {
            changed.addAll(subs.values());
        }
    }

    public int viewers(Long eventId) {
        int count = 0;
        for (Map<String, Long> subs : subscriptions.values()) {
            if (subs.containsValue(eventId)) {
                count++;
            }
        }
        return count;
    }

    /** Events someone is watching right now. */
    public Set<Long> watchedEvents() {
        Set<Long> watched = new HashSet<>();
        subscriptions.values().forEach(subs -> watched.addAll(subs.values()));
        return watched;
    }

    /** Events whose viewer count may have changed since the last call. */
    public Set<Long> drainChanged() {
        Set<Long> drained = new HashSet<>();
        for (Long eventId : changed) {
            if (changed.remove(eventId)) {
                drained.add(eventId);
            }
        }
        return drained;
    }
}
