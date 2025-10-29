package cn.yaklo.lanchat.util;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

public class IpUtil {

    public static String getClientIpAddress(HttpServletRequest request) {
        System.out.println("=== 开始获取客户端IP ===");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Remote Address: " + request.getRemoteAddr());

        String ip = null;

        // 按优先级尝试不同的头信息
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            System.out.println("Header " + headerName + ": " + headerValue);

            if (headerValue != null && !headerValue.isEmpty() && !"unknown".equalsIgnoreCase(headerValue)) {
                ip = headerValue;
                break;
            }
        }

        // 如果从头信息中没有获取到，使用远程地址
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            System.out.println("使用RemoteAddr作为IP: " + ip);
        }

        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            String[] ips = ip.split(",");
            ip = ips[0].trim();
            System.out.println("检测到代理链，使用第一个IP: " + ip);
        }

        // 处理IPv6本地地址
        if (ip != null && (ip.contains("0:0:0:0:0:0:0:1") || ip.contains("::1"))) {
            ip = "127.0.0.1";
            System.out.println("IPv6本地地址转换为127.0.0.1");
        }

        // 处理局域网IP，保持原样不转换
        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10.") ||
            (ip.startsWith("172.") && isPrivate172Network(ip)))) {
            System.out.println("检测到局域网IP，保持原样: " + ip);
        }

        System.out.println("最终获取的IP: " + ip);
        System.out.println("========================");

        return ip;
    }

    /**
     * 检查是否是172.16.0.0/12私网地址
     */
    private static boolean isPrivate172Network(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (NumberFormatException e) {
            System.err.println("解析IP地址时出错: " + ip);
        }
        return false;
    }

    public static String generateUniqueId(HttpServletRequest request) {
        String ip = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // 使用IP和User-Agent生成更唯一的标识符
        if (userAgent != null) {
            // 取User-Agent的哈希值作为额外标识
            String userAgentHash = Integer.toHexString(userAgent.hashCode());
            return ip + "#" + userAgentHash.substring(0, 8);
        }

        return ip;
    }

    public static String getSessionId(HttpServletRequest request) {
        // 尝试从session中获取唯一标识
        Object sessionId = request.getSession().getAttribute("userSessionId");
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            request.getSession().setAttribute("userSessionId", sessionId);
        }
        return sessionId.toString();
    }

    public static String getUniqueUserId(HttpServletRequest request) {
        // 结合IP、User-Agent和Session ID生成最唯一的用户标识
        String ip = getClientIpAddress(request);
        String sessionId = getSessionId(request);
        return ip + "@" + sessionId.substring(0, 8);
    }
}