package org.xbib.concurrent.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A worker pool for processing request by a number of worker threads.
 * If worker threads exit early, they are removed and finished, not reused.
 * If no worker is left, the pool closes.
 *
 * @param <R> the request type
 * @param <W> the worker type
 */
public abstract class AbstractWorkerPool<R extends Request, W extends Worker<R>>
        implements WorkerPool<R, W>, AutoCloseable {

    private static final int DEFAULT_TIMEOUT_IN_SECONDS = 30;

    private final String name;

    private final int workerCount;

    private final int timeoutInSeconds;

    private final AtomicBoolean closed;

    private final AtomicBoolean hasFailure;

    private final CountDownLatch latch;

    private final WorkerPoolListener<R, W> listener;

    private final BlockingQueue<R> queue;

    private final ThreadPoolWorkerExecutor<R, W> executor;

    public AbstractWorkerPool() {
        this("pool");
    }

    public AbstractWorkerPool(String name) {
        this(name, Runtime.getRuntime().availableProcessors());
    }

    public AbstractWorkerPool(String name,
                              int workerCount) {
        this(name, workerCount, null);
    }

    public AbstractWorkerPool(String name,
                              int workerCount,
                              WorkerPoolListener<R, W> listener) {
        this(name, workerCount, listener, DEFAULT_TIMEOUT_IN_SECONDS);
    }

    public AbstractWorkerPool(WorkerPoolListener<R, W> listener) {
        this("pool", Runtime.getRuntime().availableProcessors(), listener, DEFAULT_TIMEOUT_IN_SECONDS);
    }

    public AbstractWorkerPool(String name,
                              int workerCount,
                              WorkerPoolListener<R, W> listener,
                              int timeoutInSeconds) {
        this(name, workerCount, listener, timeoutInSeconds,
                new ThreadPoolWorkerExecutor<>(listener, workerCount,
                new LinkedBlockingQueue<>(),
                new WorkerThreadFactory(name + "-worker")));
    }

    public AbstractWorkerPool(String name,
                              int workerCount,
                              WorkerPoolListener<R, W> listener,
                              int timeoutInSeconds,
                              ThreadPoolWorkerExecutor<R, W> executor) {
        this.name = name;
        this.workerCount = workerCount;
        this.timeoutInSeconds = timeoutInSeconds;
        this.listener = listener;
        this.queue = new SynchronousQueue<>(true);
        this.closed = new AtomicBoolean(true);
        this.hasFailure = new AtomicBoolean(false);
        this.latch = new CountDownLatch(workerCount);
        this.executor = executor;
    }

    @Override
    public void open() {
        if (closed.compareAndSet(true, false)) {
            for (int i = 0; i < workerCount; i++) {
                executor.submit(newRunnable(newWorker(newWorkerName(this, i, workerCount))));
            }
        }
    }

    @Override
    public String newWorkerName(WorkerPool<R, W> workerPool, int i, int workerCount) {
        return name + "-worker-" + i + "-" + workerCount;
    }

    @Override
    public Runnable newRunnable(W worker) {
        return new DefaultRunnable<>(this, queue, getPoison(), listener, worker);
    }

    @Override
    public void execute(R request) {
        if (request.equals(getPoison())) {
            // silently ignore from outside, triggered internal only at close time
            return;
        }
        if (closed.get()) {
            IOException e = new IOException("pool is closed");
            hasFailure.set(true);
            if (listener != null) {
                listener.reject(request, e);
            }
            return;
        }
        if (latch.getCount() == 0) {
            IOException e = new IOException("pool has no more workers available");
            if (listener != null) {
                listener.reject(request, e);
            }
            return;
        }
        try {
            queue.offer(request, timeoutInSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (listener != null) {
                listener.timeout(e);
            }
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @Override
    public void close(W worker, Throwable throwable) {
        latch.countDown();
        if (throwable != null) {
            hasFailure.set(true);
            if (latch.getCount() == 0 && hasFailure.get()) {
                Collection<R> collection = new ArrayList<>();
                queue.drainTo(collection);
                if (listener != null) {
                    listener.afterfailure(collection);
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public boolean isFailed() {
        return hasFailure.get();
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            while (latch.getCount() > 0) {
                try {
                    queue.put(getPoison());
                    // wait for latch being updated by other thread
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                executor.shutdown();
                executor.awaitTermination(timeoutInSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                IOException ioException = new IOException(e);
                if (listener != null) {
                    listener.timeout(ioException);
                }
                throw ioException;
            } finally {
                if (listener != null) {
                    if (hasFailure.get()) {
                        listener.failure();
                    } else {
                        listener.success();
                    }
                }
            }
        }
    }
}
