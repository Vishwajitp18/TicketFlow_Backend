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
        // Plain WebSocket, no SockJS. SockJS's XHR-streaming/XHR-polling fallback transports
        // send their requests with withCredentials=true regardless of the server-side
        // cookie_needed flag (confirmed against this exact deployment — the CORS origin
        // response was correct, but browsers still rejected it since there's no server-side
        // way to add Access-Control-Allow-Credentials for SockJS's internal /info and
        // transport endpoints). We don't need SockJS's old-browser/restrictive-proxy fallback
        // in a modern web app, so the simplest fix is to not use it: a raw WebSocket upgrade
        // only does a one-time Origin check against setAllowedOriginPatterns below, with none
        // of SockJS's credentialed-XHR complexity.
        //
        // Under the app's context-path, e.g. wss://host/api/v1/ws.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // clients subscribe to /topic/shows/{showId}/seatmap
        registry.enableSimpleBroker("/topic");
    }
}
