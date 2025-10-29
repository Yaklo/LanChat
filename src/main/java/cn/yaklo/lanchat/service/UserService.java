package cn.yaklo.lanchat.service;

import cn.yaklo.lanchat.util.IpUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
public class UserService {

    public String getUserName(HttpServletRequest request) {
        String ip = getClientIp(request);
        return formatUserName(ip); // 格式化用户名
    }

    public String getClientIp(HttpServletRequest request) {
        return IpUtil.getClientIpAddress(request);
    }

    public String getUniqueUserId(HttpServletRequest request) {
        return IpUtil.getUniqueUserId(request);
    }

    public String formatUserName(String ip) {
        // 如果IP是本地地址，显示为"本机用户"
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
            return "本机用户";
        }
        return ip;
    }

    public String generateDisplayName(String ip, String uniqueId) {
        // 为同一IP的不同用户生成不同的显示名称
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
            return "本机用户";
        }

        // 如果唯一ID包含额外信息，提取出来
        if (uniqueId.contains("@")) {
            String[] parts = uniqueId.split("@");
            if (parts.length > 1) {
                String sessionId = parts[1];
                return ip + "(" + sessionId + ")";
            }
        }

        return ip;
    }
}