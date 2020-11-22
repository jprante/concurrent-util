package org.xbib.concurrent.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.logging.Logger;

public class WorkerPoolTest {

    @Test
    void testSimpleWorkerPool() throws Exception {
        TestWorkerPool pool = new TestWorkerPool();
        pool.open();
        for (int i = 0; i < 100; i++) {
            pool.execute(new TestRequest("simple-" + i));
        }
        pool.close();
        assertTrue(pool.isClosed());
        assertFalse(pool.isFailed());
    }

    @Test
    void testWorkerPoolWithListener() throws Exception {
        TestWorkerPool pool = new TestWorkerPool(listener);
        pool.open();
        for (int i = 0; i < 100; i++) {
            pool.execute(new TestRequest("listener-" + i));
        }
        pool.close();
        assertTrue(pool.isClosed());
        assertFalse(pool.isFailed());
    }

    @Test
    void testFailWorkerPoolWithListener() throws Exception {
        ExceptionWorkerPool pool = new ExceptionWorkerPool(listener);
        pool.open();
        for (int i = 0; i < 100; i++) {
            pool.execute(new TestRequest("fail-" + i));
        }
        pool.close();
        assertTrue(pool.isClosed());
        assertTrue(pool.isFailed());
    }

    private static final WorkerPoolListener<Request, Worker<Request>> listener = new WorkerPoolListener<>() {

        final Logger logger = Logger.getLogger("listener");

        @Override
        public void success() {
            logger.info("success");
        }

        @Override
        public void success(Request request, Worker<Request> worker) {
            logger.info("success: request = " + request + " worker = " + worker);
        }

        @Override
        public void reject(Request request, Throwable reason) {
            logger.info("rejected: request = " + request + " reason = " + reason);
        }

        @Override
        public void failure() {
            logger.info("failure");
        }

        @Override
        public void failure(Throwable throwable) {
            logger.info("failure: " + throwable);
        }

        @Override
        public void failure(Request request, Worker<Request> worker, Throwable throwable) {
            logger.info("failure: request = " + request + " worker = " + worker + " throwable = " + throwable);
        }

        @Override
        public void afterfailure(Collection<Request> requests) {
            logger.info("after failure: " + requests);
        }

        @Override
        public void timeout(Throwable throwable) {
            logger.info("timeout: " + throwable);
        }

        @Override
        public void timeout(Worker<Request> worker, Throwable throwable) {
            logger.info("timeout: worker = " + worker + " throwable = " + throwable);
        }
    };

    static class TestWorkerPool extends AbstractWorkerPool<Request, Worker<Request>> {

        TestWorkerPool() {
            super();
        }

        TestWorkerPool(WorkerPoolListener<Request, Worker<Request>> listener) {
            super(listener);
        }

        @Override
        public Worker<Request> newWorker(String name) {
            return r -> Logger.getLogger(Thread.currentThread().getName()).info(name + " executing " + r);
        }

        @Override
        public Request getPoison() {
            return TestRequest.EMPTY;
        }
    }


    static class ExceptionWorkerPool extends AbstractWorkerPool<Request, Worker<Request>> {

        ExceptionWorkerPool(WorkerPoolListener<Request, Worker<Request>> listener) {
            super(listener);
        }

        @Override
        public Worker<Request> newWorker(String name) {
            return r -> { throw new IllegalArgumentException(); };
        }

        @Override
        public Request getPoison() {
            return TestRequest.EMPTY;
        }
    }


    static class TestRequest implements Request {

        static TestRequest EMPTY = new TestRequest("");

        String name;

        TestRequest(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

    }
}
