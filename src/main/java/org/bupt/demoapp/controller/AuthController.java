package org.bupt.demoapp.controller;

import org.bupt.demoapp.dto.AuthRequest;
import org.bupt.demoapp.dto.AuthResponse;
import org.bupt.demoapp.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private AuthService authService;

    /**
     * 登录接口：POST /auth/login
     * 请求体 JSON: { "username": "...", "password": "..." }
     */
    @PostMapping("/login")
    public AuthResponse auth(@RequestBody AuthRequest authRequest) throws IllegalAccessException {
        long startTime = System.currentTimeMillis();
        logger.info("========== 收到登录请求 ==========");
        logger.info("username: {}", authRequest.getUsername());
        
        try {
            AuthResponse response = authService.login(authRequest);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("登录请求处理完成 - ok: {}, message: {}, userId: {}, userName: {}, 耗时: {}ms", 
                    response.isOk(), response.getMessage(), response.getUserId(), response.getUserName(), duration);
            logger.info("========== 登录请求完成 ==========");
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("登录请求处理失败 - username: {}, 耗时: {}ms", 
                    authRequest.getUsername(), duration, e);
            throw e;
        }
    }
}
