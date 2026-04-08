package org.bupt.demoapp.service;

public interface ChatGlobalConcurrencyService {
    boolean tryAcquire();

    void release();
}
