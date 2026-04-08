package org.bupt.demoapp.service;

import org.bupt.demoapp.entity.Intent;

public interface ChatFunctionConcurrencyService {
    boolean tryAcquire(Intent intent);

    void release(Intent intent);
}
