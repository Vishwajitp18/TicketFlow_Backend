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
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // clients subscribe to /topic/shows/{showId}/seatmap
        registry.enableSimpleBroker("/topic");
    }
}
