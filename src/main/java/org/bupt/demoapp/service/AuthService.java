package org.bupt.demoapp.service;

import org.bupt.demoapp.dto.AuthRequest;
import org.bupt.demoapp.dto.AuthResponse;

public interface AuthService {
    public AuthResponse login(AuthRequest authRequest) throws IllegalAccessException;
}
