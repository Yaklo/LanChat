package cn.yaklo.lanchat.interceptor;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpSession;
import java.util.Map;

@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 获取客户端真实IP地址
        String clientIp = getClientIpAddress(request);

        System.out.println("=== WebSocket握手阶段 ===");
        System.out.println("请求URI: " + request.getURI());
        System.out.println("客户端IP: " + clientIp);
        System.out.println("请求Headers: " + request.getHeaders().toSingleValueMap());

        // 将客户端IP存储到WebSocket session属性中
        attributes.put("clientIp", clientIp);
        attributes.put("handshakeTime", System.currentTimeMillis());

        // 尝试从HTTP Session中获取自定义用户名
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpSession httpSession = servletRequest.getServletRequest().getSession(false);
            if (httpSession != null) {
                String customUserName = (String) httpSession.getAttribute("customUserName");
                if (customUserName != null && !customUserName.trim().isEmpty()) {
                    attributes.put("customUserName", customUserName);
                    System.out.println("从HTTP Session获取到自定义用户名: " + customUserName);
                }
            }
        }

        System.out.println("握手成功，IP已存储到session属性");
        System.out.println("====================");

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            System.err.println("WebSocket握手后发生异常: " + exception.getMessage());
            exception.printStackTrace();
        } else {
            System.out.println("WebSocket握手完成，连接建立成功");
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        String ip = null;

        // 1. 尝试从X-Forwarded-For头获取（代理情况）
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            String[] ips = xForwardedFor.split(",");
            for (String ipStr : ips) {
                if (ipStr != null && !ipStr.trim().isEmpty() && !"unknown".equalsIgnoreCase(ipStr.trim())) {
                    ip = ipStr.trim();
                    break;
                }
            }
        }

        // 2. 尝试从X-Real-IP头获取（Nginx代理）
        if (ip == null) {
            String xRealIp = request.getHeaders().getFirst("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
                ip = xRealIp.trim();
            }
        }

        // 3. 尝试从Proxy-Client-IP头获取
        if (ip == null) {
            String proxyClientIp = request.getHeaders().getFirst("Proxy-Client-IP");
            if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
                ip = proxyClientIp.trim();
            }
        }

        // 4. 尝试从WL-Proxy-Client-IP头获取
        if (ip == null) {
            String wlProxyClientIp = request.getHeaders().getFirst("WL-Proxy-Client-IP");
            if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
                ip = wlProxyClientIp.trim();
            }
        }

        // 5. 从HTTP连接中获取远程地址
        if (ip == null) {
            ip = request.getRemoteAddress().getAddress().getHostAddress();
        }

        // 处理IPv6本地地址
        if (ip != null && (ip.contains("0:0:0:0:0:0:0:1") || ip.contains("::1"))) {
            ip = "127.0.0.1";
        }

        // 处理IPv4本地地址
        if (ip != null && ip.startsWith("192.168.")) {
            // 保持局域网IP不变
            return ip;
        }

        if (ip != null && ip.startsWith("10.")) {
            // 保持局域网IP不变
            return ip;
        }

        if (ip != null && ip.startsWith("172.")) {
            // 保持172.16.0.0/12网段IP不变
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int secondOctet = Integer.parseInt(parts[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return ip;
                }
            }
        }

        return ip;
    }
}