package org.xbib.concurrent.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolWorkerExecutor<R extends Request, W extends Worker<R>> extends ThreadPoolExecutor {

    private final WorkerPoolListener<R, W> listener;

    public ThreadPoolWorkerExecutor(WorkerPoolListener<R, W> listener,
                                    int numThreads,
                                    BlockingQueue<Runnable> blockingQueue,
                                    ThreadFactory threadFactory) {
        super(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, blockingQueue, threadFactory);
        this.listener = listener;
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable terminationCause) {
        super.afterExecute(runnable, terminationCause);
        if (terminationCause == null && runnable instanceof Future<?>) {
            try {
                Future<?> future = (Future<?>) runnable;
                if (future.isDone()) {
                    future.get();
                }
            } catch (CancellationException | ExecutionException ce) {
                if (listener != null) {
                    listener.failure(ce);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (listener != null) {
                    listener.timeout(ie);
                }
            }
        }
    }
}
