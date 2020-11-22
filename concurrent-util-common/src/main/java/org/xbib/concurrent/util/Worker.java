package org.xbib.concurrent.util;

import java.io.IOException;

/**
 * Worker interface for executing requests.
 *
 * @param <R> request type
 */
@FunctionalInterface
public interface Worker<R extends Request> {
    /**
     * Execute a request.
     *
     * @param request request
     * @throws IOException if execution fails
     */
    void execute(R request) throws IOException;
}
