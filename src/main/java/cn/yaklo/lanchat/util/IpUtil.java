package cn.yaklo.lanchat.util;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

public class IpUtil {

    public static String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 处理IPv6本地地址
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
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