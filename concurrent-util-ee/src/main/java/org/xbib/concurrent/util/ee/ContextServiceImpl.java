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

import org.xbib.concurrent.util.ee.api.ContextService;
import org.xbib.concurrent.util.ee.internal.ContextProxyInvocationHandler;
import org.xbib.concurrent.util.ee.spi.ContextSetupProvider;
import org.xbib.concurrent.util.ee.spi.TransactionSetupProvider;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Enumeration;
import java.util.Map;

/**
 * Implementation of ContextService interface
 */
public class ContextServiceImpl implements ContextService, Serializable {

    static final long serialVersionUID = -386695836029966433L;
    
    protected final ContextSetupProvider contextSetupProvider;
    protected final TransactionSetupProvider transactionSetupProvider;
    protected final String name;
    
    final private static String INVALID_PROXY = 
            "contextObject is not a valid contextual object proxy created with the createContextualProxy method";
    final private static String NULL_CONTEXTPROPERTIES = 
            "contextProperties cannot be null";
    final private static String NULL_INSTANCE =
            "instance cannot be null";
    final private static String NO_INTERFACES =
            "No interfaces is provided in the method argument";
    final private static String CLASS_DOES_NOT_IMPLEMENT_INTERFACES =
            "Class does not implement at least one of the provided interfaces";
    final private static String DIFFERENT_CONTEXTSERVICE =
            "Proxy is created by a different ContextService object";
    
    public ContextServiceImpl(String name, ContextSetupProvider contextSetupProvider) {
        this(name, contextSetupProvider, null);
    }

    public ContextServiceImpl(String name, ContextSetupProvider contextSetupProvider, 
            TransactionSetupProvider transactionSetupProvider) {
        this.name = name;
        this.contextSetupProvider = contextSetupProvider;
        this.transactionSetupProvider = transactionSetupProvider;
    }

    public String getName() {
        return name;
    }

    public ContextSetupProvider getContextSetupProvider() {
        return contextSetupProvider;
    }
    
    public TransactionSetupProvider getTransactionSetupProvider() {
        return transactionSetupProvider;
    }
    
    @Override
    public Object createContextualProxy(Object instance, Class<?>... interfaces) {
        return createContextualProxy(instance, null, interfaces);
    }

    @Override
    public Object createContextualProxy(Object instance, Map<String, String> executionProperties, Class<?>... interfaces) {
        if (instance == null) {
            throw new IllegalArgumentException(NULL_INSTANCE); 
        }
        if (interfaces == null || interfaces.length == 0) {
            throw new IllegalArgumentException(NO_INTERFACES);
        }
        Class<?> instanceClass = instance.getClass();
        for (Class<?> thisInterface: interfaces) {
            if (!thisInterface.isAssignableFrom(instanceClass)) {
                throw new IllegalArgumentException(CLASS_DOES_NOT_IMPLEMENT_INTERFACES);
            }  
        }
        ContextProxyInvocationHandler handler = new ContextProxyInvocationHandler(this, instance, executionProperties);
        return Proxy.newProxyInstance(instance.getClass().getClassLoader(), interfaces, handler);
    }

    @Override
    public <T> T createContextualProxy(T instance, Class<T> intf) {
        return createContextualProxy(instance, null, intf);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createContextualProxy(T instance, Map<String, String> executionProperties, Class<T> intf) {
        if (instance == null) {
            throw new IllegalArgumentException(NULL_INSTANCE); 
        }
        if (intf == null) {
            throw new IllegalArgumentException(NO_INTERFACES);
        }
        ContextProxyInvocationHandler handler = new ContextProxyInvocationHandler(this, instance, executionProperties);
        return (T) Proxy.newProxyInstance(instance.getClass().getClassLoader(), new Class<?>[]{intf}, handler);
    }
    
    @Override
    public Map<String, String> getExecutionProperties(Object contextObject) {
        ContextProxyInvocationHandler handler = verifyHandler(contextObject);
        return handler.getExecutionProperties();
    }
    
    protected void verifyStringValue(Enumeration<?> e) throws ClassCastException {
        while (e.hasMoreElements()) {
            String value = (String)e.nextElement();
        }
    }

    protected ContextProxyInvocationHandler verifyHandler(Object contextObject) {
        InvocationHandler handler = Proxy.getInvocationHandler(contextObject);
        if (handler instanceof ContextProxyInvocationHandler) {
            ContextProxyInvocationHandler cpih = (ContextProxyInvocationHandler) handler;
            if (cpih.getContextService() != this) {
                throw new IllegalArgumentException(DIFFERENT_CONTEXTSERVICE);
            }
            return cpih;
        }
        throw new IllegalArgumentException(INVALID_PROXY);
    }
}
