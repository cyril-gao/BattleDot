package com.example;

import java.util.concurrent.CountDownLatch;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class Latch {
    private CountDownLatch latch;

    public Latch(int count) {
        latch = new CountDownLatch(count);
    }

    public Latch() { this(1); }

    public void await() throws InterruptedException {
        latch.await();
    }

    public void countDown() {
        latch.countDown();
    }
}
