package com.hft.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket/STOMP configuration for real-time push to browser/mobile clients.
 *
 * Client connection:
 *   const socket = new SockJS('http://localhost:8080/ws');
 *   const client = Stomp.over(socket);
 *   client.connect({}, () => {
 *     client.subscribe('/topic/signals', msg => console.log(msg.body));
 *     client.subscribe('/topic/quotes/AAPL', msg => console.log(msg.body));
 *   });
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients subscribe to /topic/* (broadcast) and /queue/* (user-specific)
        registry.enableSimpleBroker("/topic", "/queue");

        // Client sends to /app/* → mapped to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // User-specific queue prefix
        registry.setUserDestinationPrefix("/user");
    }
}