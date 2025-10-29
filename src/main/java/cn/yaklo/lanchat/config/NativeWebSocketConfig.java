package cn.yaklo.lanchat.config;

import cn.yaklo.lanchat.controller.WebSocketNativeController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
public class NativeWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketNativeController webSocketNativeController;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketNativeController, "/ws/chat")
                .setAllowedOrigins("*");
    }
}