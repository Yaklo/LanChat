package cn.yaklo.lanchat.config;

import cn.yaklo.lanchat.controller.WebSocketNativeController;
import cn.yaklo.lanchat.interceptor.WebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSocket
public class NativeWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketNativeController webSocketNativeController;

    @Autowired
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketNativeController, "/ws/chat")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }

    /**
     * 配置WebSocket的最大消息大小
     * 设置为128KB，用于发送文本消息
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置最大文本消息大小为128KB
        container.setMaxTextMessageBufferSize(128 * 1024);
        // 设置最大二进制消息大小为128KB
        container.setMaxBinaryMessageBufferSize(128 * 1024);
        // 设置消息最大空闲时间（单位秒）
        container.setMaxSessionIdleTimeout(300000L);
        return container;
    }
}