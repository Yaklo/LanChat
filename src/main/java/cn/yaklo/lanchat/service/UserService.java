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
        // 统一返回"无名氏"作为默认用户名
        return "无名氏";
    }
}