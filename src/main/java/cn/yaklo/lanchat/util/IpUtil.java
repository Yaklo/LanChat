package cn.yaklo.lanchat.util;

import javax.servlet.http.HttpServletRequest;

public class IpUtil {

    /**
     * 获取客户端真实IP地址
     * 支持代理环境和局域网IP获取
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        String ip = null;

        // 按优先级尝试不同的代理头信息
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
            if (headerValue != null && !headerValue.isEmpty() && !"unknown".equalsIgnoreCase(headerValue)) {
                ip = headerValue;
                break;
            }
        }

        // 如果没有获取到代理头信息，使用远程地址
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            String[] ips = ip.split(",");
            ip = ips[0].trim();
        }

        // 处理IPv6本地地址转换
        if (ip != null && (ip.contains("0:0:0:0:0:0:0:1") || ip.contains("::1"))) {
            ip = "127.0.0.1";
        }

        // 局域网IP保持原样（192.168.x.x, 10.x.x.x, 172.16-31.x.x）
        // 不进行转换，保持真实的局域网IP地址

        return ip;
    }

    /**
     * 简单的会话标识生成（用于基本的会话管理）
     */
    public static String getSessionId(HttpServletRequest request) {
        Object sessionId = request.getSession().getAttribute("userSessionId");
        if (sessionId == null) {
            sessionId = System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
            request.getSession().setAttribute("userSessionId", sessionId);
        }
        return sessionId.toString();
    }
}