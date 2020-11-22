/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.xbib.concurrent.util.ee;

import org.xbib.concurrent.util.ee.api.ManagedThreadFactory;
import org.xbib.concurrent.util.ee.internal.ManagedFutureTask;
import org.xbib.concurrent.util.ee.internal.ThreadExpiredException;
import org.xbib.concurrent.util.ee.spi.ContextHandle;
import org.xbib.concurrent.util.ee.spi.ContextSetupProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of ManagedThreadFactory interface.
 */
public class ManagedThreadFactoryImpl implements ManagedThreadFactory {

    private List<AbstractManagedThread> threads;
    private boolean stopped = false;
    private Lock lock; // protects threads and stopped

    private String name;
    final private ContextSetupProvider contextSetupProvider;
    // A non-null ContextService should be provided if thread context should
    // be setup before running the Runnable passed in through the newThread
    // method.
    // Context service could be null if the ManagedThreadFactoryImpl is
    // used for creating threads for ManagedExecutorService, where it is
    // not necessary to set up thread context at thread creation time. In that
    // case, thread context is set up before running each task.
    final private ContextServiceImpl contextService;
    private int priority;
    private long hungTaskThreshold = 0L; // in milliseconds
    private AtomicInteger threadIdSequence = new AtomicInteger();

    public static final String MANAGED_THREAD_FACTORY_STOPPED = "ManagedThreadFactory is stopped";

    public ManagedThreadFactoryImpl(String name) {
        this(name, null, Thread.NORM_PRIORITY);
    }

    public ManagedThreadFactoryImpl(String name, ContextServiceImpl contextService) {
        this(name, contextService, Thread.NORM_PRIORITY);
    }

    public ManagedThreadFactoryImpl(String name,
                                    ContextServiceImpl contextService,
                                    int priority) {
        this.name = name;
        this.contextService = contextService;
        this.contextSetupProvider = contextService != null? contextService.getContextSetupProvider(): null;
        this.priority = priority;
        threads = new ArrayList<>();
        lock = new ReentrantLock();
    }

    public String getName() {
        return name;
    }

    public long getHungTaskThreshold() {
        return hungTaskThreshold;
    }

    public void setHungTaskThreshold(long hungTaskThreshold) {
        this.hungTaskThreshold = hungTaskThreshold;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        lock.lock();
        try {
            if (stopped) {
                // Do not create new thread and throw IllegalStateException if stopped
                throw new IllegalStateException(MANAGED_THREAD_FACTORY_STOPPED);
            }
            ContextHandle contextHandleForSetup = null;
            if (contextSetupProvider != null) {
                contextHandleForSetup = contextSetupProvider.saveContext(contextService);
            }
            AbstractManagedThread newThread = createThread(r, contextHandleForSetup);
            newThread.setPriority(priority);
            newThread.setDaemon(true);
            threads.add(newThread);
            return newThread;
        }
        finally {
            lock.unlock();
        }
    }

    protected AbstractManagedThread createThread(final Runnable r, final ContextHandle contextHandleForSetup) {
        if (System.getSecurityManager() == null) {
            return new ManagedThread(r, contextHandleForSetup);
        } else {
            return (ManagedThread) AccessController.doPrivileged(
                    (PrivilegedAction<?>) () -> new ManagedThread(r, contextHandleForSetup));
        }
    }
    
    protected void removeThread(ManagedThread t) {
        lock.lock();
        try {
            threads.remove(t);
        }
        finally {
            lock.unlock();
        }
    }
    
    /**
     * Return an array of threads in this ManagedThreadFactoryImpl
     * @return an array of threads in this ManagedThreadFactoryImpl.
     *         It returns null if there is no thread.
     */
    protected Collection<AbstractManagedThread> getThreads() {
        Collection<AbstractManagedThread> result = null;
        lock.lock();
        try {
            if (!threads.isEmpty()) {
                result = new ArrayList<>(threads);
            }
        }
        finally {
            lock.unlock();
        }
        return result;
    }
    public void taskStarting(Thread t, ManagedFutureTask<?> task) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = System.currentTimeMillis();
            mt.task = task;
        }
    }
    
    public void taskDone(Thread t) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = 0L;
            mt.task = null;
        }
    }


    /**
     * Stop the ManagedThreadFactory instance. This should be used by the
     * component that creates the ManagedThreadFactory when the component is
     * stopped. All threads that this ManagedThreadFactory has created using
     * the #newThread() method are interrupted.
     */
    public void stop() {
      lock.lock();
      try {
        stopped = true;
        // interrupt all the threads created by this factory
        Iterator<AbstractManagedThread> iter = threads.iterator();
        while(iter.hasNext()) {
            AbstractManagedThread t = iter.next();
            try {
               t.shutdown(); // mark threads as shutting down
               t.interrupt();
            } catch (SecurityException ignore) {                
            }
        }
      }
      finally {
          lock.unlock();
      }      
    }
    
    /**
     * ManageableThread to be returned by {@code ManagedThreadFactory.newThread()}
     */
    public class ManagedThread extends AbstractManagedThread {
        final ContextHandle contextHandleForSetup;
        volatile ManagedFutureTask<?> task = null;
        volatile long taskStartTime = 0L;
        
        public ManagedThread(Runnable target, ContextHandle contextHandleForSetup) {
            super(target);
            setName(name + "-Thread-" + threadIdSequence.incrementAndGet());
            this.contextHandleForSetup = contextHandleForSetup;
        }

        @Override
        public void run() {
            ContextHandle handle = null;
            try {
                if (contextHandleForSetup != null) {
                    handle = contextSetupProvider.setup(contextHandleForSetup);
                }
                if (shutdown) {
                    // start thread in interrupted state if already marked for shutdown
                    this.interrupt();
                }
                super.run();
            } catch (ThreadExpiredException ex) {
            } catch (Throwable t) {
            } finally {
                if (handle != null) {
                    contextSetupProvider.reset(handle);
                }
                removeThread(this);
            }
        }
        
        @Override
        boolean cancelTask() {
            if (task != null) {
                return task.cancel(true);
            }
            return false;
        }

        @Override
        public String getTaskIdentityName() {
            if (task != null) {
                return task.getTaskIdentityName();
            }
            return "null";
        }

        @Override
        public long getTaskRunTime(long now) {
            if (task != null && taskStartTime > 0) {
                long taskRunTime = now - taskStartTime;
                return taskRunTime > 0 ? taskRunTime : 0;
            }
            return 0;
        }

        @Override
        public long getThreadStartTime() {
            return threadStartTime;
        }

        @Override
        boolean isTaskHung(long now) {
            if (hungTaskThreshold > 0) {
                return getTaskRunTime(now) - hungTaskThreshold > 0;
            }
            return false;
        }

    }
}
