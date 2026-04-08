package org.bupt.demoapp.service;

public interface ChatRateLimitService {
    boolean tryAcquire();
}
