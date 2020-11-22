package org.xbib.concurrent.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultRunnable<R extends Request, W extends Worker<R>> implements Runnable, Closeable {

    private final WorkerPool<R, W> pool;

    private final BlockingQueue<R> queue;

    private final R poison;

    private final WorkerPoolListener<R, W> listener;

    private final W worker;

    private final AtomicBoolean closed;

    public DefaultRunnable(WorkerPool<R, W> pool,
                           BlockingQueue<R> queue,
                           R poison,
                           WorkerPoolListener<R, W> listener,
                           W worker) {
        this.pool = pool;
        this.queue = queue;
        this.poison = poison;
        this.listener = listener;
        this.worker = worker;
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public void run() {
        R request = null;
        Throwable throwable = null;
        try {
            while (!closed.get()) {
                request = queue.take();
                if (poison.equals(request)) {
                    break;
                }
                worker.execute(request);
                if (listener != null) {
                    listener.success(request, worker);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (listener != null) {
                listener.timeout(worker, e);
            }
        } catch (Exception | AssertionError e) {
            if (listener != null) {
                listener.failure(request, worker, e);
            }
            throwable = e;
            throw new UncheckedIOException(new IOException(e));
        } finally {
            pool.close(worker, throwable);
        }
    }
}
