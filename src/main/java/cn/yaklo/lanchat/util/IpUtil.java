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
        // 生成真正唯一的用户标识，包含IP、浏览器指纹和会话信息
        String ip = getClientIpAddress(request);
        String sessionId = getSessionId(request);
        String userAgent = request.getHeader("User-Agent");

        // 生成浏览器指纹（基于User-Agent的哈希值）
        String browserFingerprint = generateBrowserFingerprint(userAgent);

        // 组合生成真正的唯一ID
        String uniqueId = ip + "#" + browserFingerprint + "@" + sessionId.substring(0, 8);

        System.out.println("=== 生成用户唯一ID ===");
        System.out.println("IP: " + ip);
        System.out.println("Browser Fingerprint: " + browserFingerprint);
        System.out.println("Session ID: " + sessionId);
        System.out.println("最终UniqueID: " + uniqueId);
        System.out.println("====================");

        return uniqueId;
    }

    /**
     * 生成浏览器指纹
     */
    private static String generateBrowserFingerprint(String userAgent) {
        if (userAgent == null) {
            userAgent = "Unknown";
        }

        // 使用User-Agent的哈希值作为浏览器指纹
        try {
            // 简单的哈希算法：取User-Agent的前几个字符的哈希
            String normalized = userAgent.toLowerCase()
                .replace(" ", "")
                .replace("/", "")
                .replace(";", "")
                .replace(":", "");

            if (normalized.length() > 20) {
                normalized = normalized.substring(0, 20);
            }

            return Integer.toHexString(normalized.hashCode()).substring(0, 8);
        } catch (Exception e) {
            System.err.println("生成浏览器指纹失败: " + e.getMessage());
            return "default";
        }
    }

    /**
     * 生成临时会话ID（用于WebSocket连接）
     */
    public static String generateTemporarySessionId(String ip, String userAgent) {
        String fingerprint = generateBrowserFingerprint(userAgent);
        long timestamp = System.currentTimeMillis();

        // 临时ID格式：ip#fingerprint_timestamp
        return ip + "#" + fingerprint + "_" + timestamp;
    }
}