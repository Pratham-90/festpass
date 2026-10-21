package com.festpass.realtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at /ws with Spring's simple in-memory broker (PLAN.md section 7).
 * The handshake goes through Spring Security, so a logged-in browser's session cookie makes its
 * user the STOMP user. Anonymous visitors can still connect to watch public topics.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompGuard guard;
    private final String[] allowedOrigins;
    private final ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();

    public WebSocketConfig(StompGuard guard, @Value("${festpass.websocket.allowed-origins}") String[] allowedOrigins) {
        this.guard = guard;
        this.allowedOrigins = allowedOrigins;
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("ws-heartbeat-");
        heartbeats.setDaemon(true);
        heartbeats.initialize();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same-origin is always allowed; this adds the Vite dev server.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
        // Not setPreserveReceiveOrder(true): it hands frames to another thread, so a frame refused by
        // StompGuard would be dropped silently instead of answered with an ERROR frame.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                // Heartbeats every 10 s notice phones that drop off the network, so viewer counts stay honest.
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // Deliver messages to each client in the order they were published (HELD before AVAILABLE).
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard);
    }

    @PreDestroy
    void stopHeartbeats() {
        heartbeats.shutdown();
    }
}
