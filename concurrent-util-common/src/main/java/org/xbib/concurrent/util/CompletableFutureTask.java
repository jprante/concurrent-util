package org.xbib.concurrent.util;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;

/**
 * A completable future task.
 *
 * @param <T> the task type parameter
 */
public class CompletableFutureTask<T> extends AbstractFuture<T> implements RunnableFuture<T> {

    private final Callable<T> callable;

    public CompletableFutureTask(Callable<T> callable) {
        this.callable = callable;
    }

    @Override
    public void run() {
        try {
            set(callable.call());
        } catch (Exception e) {
            setException(e);
        } catch (Throwable t) {
            setException(new RuntimeException(t));
        }
    }
}
