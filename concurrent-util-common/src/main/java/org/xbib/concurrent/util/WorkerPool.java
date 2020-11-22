package org.xbib.concurrent.util;

import java.io.IOException;

/**
 * Interface for a worker pool.
 *
 * @param <W> the worker
 */
public interface WorkerPool<R extends Request, W extends Worker<R>> extends AutoCloseable {

    void open();

    String newWorkerName(WorkerPool<R, W> workerPool, int i, int totalWorkerCount);

    W newWorker(String name);

    Runnable newRunnable(W worker);

    void execute(R request);

    void close() throws IOException;

    void close(W worker, Throwable throwable);

    R getPoison();

    boolean isClosed();

    boolean isFailed();
}
