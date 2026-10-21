package com.festpass.realtime;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decides what browsers may do over STOMP. Only the server publishes: clients can't SEND,
 * so nobody can fake a seat update. Clients may only SUBSCRIBE to known destinations:
 * <ul>
 *   <li>/topic/events/{id}/seats, /stats, /queue: anyone, including visitors who aren't logged in;</li>
 *   <li>/topic/events/{id}/checkins: gate staff, organizers and admins;</li>
 *   <li>/topic/arena/**: admins;</li>
 *   <li>/user/queue/holds and /user/queue/admission: logged-in users (each sees only their own).</li>
 * </ul>
 * A refused frame closes the connection with a STOMP ERROR frame.
 */
@Component
public class StompGuard implements ChannelInterceptor {

    static final Pattern EVENT_TOPIC = Pattern.compile("^/topic/events/(\\d+)/(seats|stats|queue|checkins)$");
    private static final Set<String> USER_QUEUES = Set.of("/user/queue/holds", "/user/queue/admission");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        if (stomp.getCommand() == StompCommand.SEND) {
            throw new MessageDeliveryException("Clients can't send messages here");
        }
        if (stomp.getCommand() == StompCommand.SUBSCRIBE) {
            checkSubscription(stomp.getDestination(), stomp.getUser());
        }
        return message;
    }

    private static void checkSubscription(String destination, Principal user) {
        if (destination == null) {
            throw new MessageDeliveryException("A destination is required");
        }
        Matcher event = EVENT_TOPIC.matcher(destination);
        if (event.matches()) {
            if (event.group(2).equals("checkins")) {
                requireRole(user, "ROLE_GATE_STAFF", "ROLE_ORGANIZER", "ROLE_ADMIN");
            }
            return;
        }
        if (destination.startsWith("/topic/arena/")) {
            requireRole(user, "ROLE_ADMIN");
            return;
        }
        if (USER_QUEUES.contains(destination)) {
            if (!(user instanceof Authentication auth) || !auth.isAuthenticated()) {
                throw new MessageDeliveryException("Log in to subscribe to " + destination);
            }
            return;
        }
        throw new MessageDeliveryException("Unknown destination " + destination);
    }

    private static void requireRole(Principal user, String... roles) {
        if (user instanceof Authentication auth && auth.isAuthenticated()) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                for (String role : roles) {
                    if (role.equals(authority.getAuthority())) {
                        return;
                    }
                }
            }
        }
        throw new MessageDeliveryException("Not allowed to subscribe here");
    }
}
