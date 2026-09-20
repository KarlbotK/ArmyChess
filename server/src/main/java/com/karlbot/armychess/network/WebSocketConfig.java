package com.karlbot.armychess.network;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(GameWebSocketHandler handler,
                           @Value("${armychess.allowed-origins}") String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game").setAllowedOrigins(allowedOrigins);
    }
}
