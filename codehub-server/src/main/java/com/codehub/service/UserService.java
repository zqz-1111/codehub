package com.codehub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codehub.dto.LoginRequest;
import com.codehub.dto.RegisterRequest;
import com.codehub.dto.UserVO;
import com.codehub.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    UserVO register(RegisterRequest request);

    /**
     * 用户登录，返回 {token, user}
     */
    Map<String, Object> login(LoginRequest request);

    /**
     * 根据用户ID获取用户信息
     */
    UserVO getUserVO(Long userId);

    /**
     * 用户登出（JWT黑名单）
     */
    void logout(String token);
}
