package org.bupt.demoapp.serviceImp;

import org.bupt.demoapp.common.Messages;
import org.bupt.demoapp.common.SnowflakeIdGenerator;
import org.bupt.demoapp.dto.AuthRequest;
import org.bupt.demoapp.dto.AuthResponse;
import org.bupt.demoapp.entity.User;
import org.bupt.demoapp.mapper.UserMapper;
import org.bupt.demoapp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
@Service
public class AuthServiceImp implements AuthService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Autowired
    PasswordEncoder passwordEncoder;


    @Override
    public AuthResponse login(AuthRequest authRequest) throws IllegalAccessException {
        // 入参校验：任何一个为空都视为非法
        if (authRequest == null
                || !StringUtils.hasText(authRequest.getUsername())
                || !StringUtils.hasText(authRequest.getPassword())) {
            throw new IllegalAccessException(Messages.AUTH_INVALID_INPUT);
        }

        String username = authRequest.getUsername();
        String password = authRequest.getPassword();
        //查用户名
        User user=userMapper.findByUsername(username);
        //如果用户不存在,注册
        if(user==null){
            Long userId=snowflakeIdGenerator.nextId();
            User newUser=new User();
            newUser.setUsername(username);
            newUser.setId(userId);
            newUser.setEncryptedPassword(passwordEncoder.encode(password));
            int rows=userMapper.insert(newUser);
            if(rows!=1){
                return new AuthResponse(
                        false,
                        Messages.AUTH_REGISTER_FAILED,
                        null,
                        null
                );
            }
            return new AuthResponse(
                    true,
                    Messages.AUTH_REGISTER_SUCCESS,
                    userId,
                    username
            );
        }


        //用户存在
        boolean match=passwordEncoder.matches(password,user.getEncryptedPassword());
        if(!match){
            return new AuthResponse(
                    false,
                    Messages.AUTH_WRONG_CREDENTIALS,
                    null,
                    null
            );
        }
        return new AuthResponse(
                true,
                Messages.AUTH_LOGIN_SUCCESS,
                user.getId(),
                user.getUsername()
        );

    }
}
