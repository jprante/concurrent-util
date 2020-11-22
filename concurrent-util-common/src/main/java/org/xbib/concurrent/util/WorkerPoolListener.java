package org.xbib.concurrent.util;

import java.util.Collection;

/**
 * A callback listener for the state of the worker pool.
 * Receives events of success or failure.
 *
 * @param <W> the worker type parameter
 */
public interface WorkerPoolListener<R extends Request, W extends Worker<R>> {

    /**
     * Emits success if all workers were terminated without exception.
     */
    void success();

    /**
     * Emits success of a single request on a worker.
     *
     * @param request the request
     * @param worker the worker
     */
    void success(R request, W worker);

    void reject(R request, Throwable reason);

    /**
     * Emits failure of this pool, if one worker was terminated with an exception.
     */
    void failure();

    /**
     * Emits failure of this pool with an exception.
     */
    void failure(Throwable throwable);

    /**
     * Emits a worker failure.
     * @param worker the worker
     * @param throwable the exception
     */
    void failure(R request, W worker, Throwable throwable);

    void afterfailure(Collection<R> requests);

    /**
     * Timeout of the worker pool.
     * @param throwable the timeout exception
     */
    void timeout(Throwable throwable);

    /**
     * Timeout of a worker.
     * @param throwable the timeout exception
     */
    void timeout(W worker, Throwable throwable);

}
