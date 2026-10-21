package com.festpass.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A real STOMP-over-WebSocket client for tests, connecting the way the browser will. */
class StompTestClient implements AutoCloseable {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final StompSession session;
    private final SimpleBrokerMessageHandler broker;
    private final SimpUserRegistry users;
    private final String username;
    final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

    /**
     * @param cookie   a "JSESSIONID=..." header to connect as a logged-in user, or null for a visitor
     * @param username the logged-in user's email, or null for a visitor
     */
    StompTestClient(int port, String cookie, String username, ObjectMapper objectMapper,
                    SimpleBrokerMessageHandler broker, SimpUserRegistry users) throws Exception {
        this.broker = broker;
        this.users = users;
        this.username = username;
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) {
            headers.add("Cookie", cookie);
        }
        session = client.connectAsync("ws://localhost:" + port + "/ws", headers, new StompHeaders(), new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message"))); // only ERROR frames arrive here
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers, byte[] payload, Throwable e) {
                errors.add(String.valueOf(e.getMessage()));
            }
        }).get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Subscribes and waits until the server's broker has registered the subscription. */
    BlockingQueue<JsonNode> subscribe(String destination) throws Exception {
        int before = destination.startsWith("/user/") ? 0 : subscribers(destination);
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((JsonNode) payload);
            }
        });
        if (destination.startsWith("/user/")) {
            await(() -> userSubscriptionRegistered(destination), destination);
        } else {
            await(() -> subscribers(destination) > before, destination);
        }
        return messages;
    }

    /** Tries to subscribe without waiting; for destinations the server should refuse. */
    void subscribeQuietly(String destination) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });
    }

    void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    /** The first message matching the condition, waiting up to 5 seconds. Skips other messages. */
    static JsonNode next(BlockingQueue<JsonNode> messages, Predicate<JsonNode> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && condition.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No matching message arrived within " + WAIT.toSeconds() + " s");
    }

    private int subscribers(String destination) {
        return broker.getSubscriptionRegistry().findSubscriptions(probe(destination)).values().stream()
                .mapToInt(java.util.List::size).sum();
    }

    /**
     * Spring turns /user/queue/x into /queue/x-user{sessionId} for the broker. Find this user's
     * session that asked for the destination, then check the broker has the translated subscription.
     */
    private boolean userSubscriptionRegistered(String destination) {
        SimpUser user = username == null ? null : users.getUser(username);
        if (user == null) {
            return false;
        }
        for (SimpSession s : user.getSessions()) {
            boolean asked = s.getSubscriptions().stream().anyMatch(sub -> sub.getDestination().equals(destination));
            String translated = destination.substring("/user".length()) + "-user" + s.getId();
            if (asked && !broker.getSubscriptionRegistry().findSubscriptions(probe(translated)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static org.springframework.messaging.Message<byte[]> probe(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** Polls a condition (not a fixed sleep): returns as soon as the server has caught up. */
    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Subscription to " + what + " was not registered");
    }

    /** The server may already have closed the connection (it does after an ERROR frame). */
    @Override
    public void close() {
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (RuntimeException alreadyClosed) {
            // nothing left to close
        }
    }
}
