package org.xbib.concurrent.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerThreadFactory implements ThreadFactory {

    private final String name;

    private final AtomicInteger counter;

    public WorkerThreadFactory(String name) {
        this.name = name;
        this.counter = new AtomicInteger();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        return new Thread(runnable, name + "-" + counter.getAndIncrement());
    }
}
