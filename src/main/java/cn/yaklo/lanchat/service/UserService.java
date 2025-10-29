package cn.yaklo.lanchat.service;

import cn.yaklo.lanchat.util.IpUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
public class UserService {

    public String getUserName(HttpServletRequest request) {
        String ip = getClientIp(request);
        return formatUserName(ip);
    }

    public String getClientIp(HttpServletRequest request) {
        return IpUtil.getClientIpAddress(request);
    }

    public String formatUserName(String ip) {
        // 如果IP是本地地址，显示为"本机用户"
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip) || ip == null) {
            return "本机用户";
        }
        return ip;
    }
}