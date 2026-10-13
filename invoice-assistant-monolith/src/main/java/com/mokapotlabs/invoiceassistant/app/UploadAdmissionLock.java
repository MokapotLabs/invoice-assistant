package com.mokapotlabs.invoiceassistant.app;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class UploadAdmissionLock {
    private final ReentrantLock lock = new ReentrantLock();

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
