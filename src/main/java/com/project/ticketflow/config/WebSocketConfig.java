package com.project.ticketflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Server-push only — the frontend never sends a STOMP message here, it just subscribes.
 * Seat *actions* (hold/confirm/cancel) still go through the normal REST API; this socket
 * exists purely to replace polling GET /shows/{id}/seatmap with a live feed of the deltas
 * (see SeatStatusChangedEvent / SeatMapEventListener for where those get published).
 *
 * No application-destination prefix is registered since there's nothing for a client to
 * send — just the simple broker for server → client broadcast.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // under the app's context-path, e.g. /api/v1/ws — SockJS fallback for networks that
        // block raw WebSocket upgrades
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                // We're fully stateless (JWT, no server-side sessions, no sticky-session
                // load balancing) — there's no reason for SockJS to want a session-affinity
                // cookie. Leaving this at its default (true) makes SockJS's XHR-streaming/
                // XHR-polling fallback transports send requests with withCredentials=true,
                // which browsers reject outright unless the server also sends
                // Access-Control-Allow-Credentials: true (it doesn't) — surfaces in the
                // browser as a CORS error even though the actual CORS origin config is fine.
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // clients subscribe to /topic/shows/{showId}/seatmap
        registry.enableSimpleBroker("/topic");
    }
}
