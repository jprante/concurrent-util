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

package org.xbib.concurrent.util.ee.internal;

import org.xbib.concurrent.util.ee.ContextServiceImpl;
import org.xbib.concurrent.util.ee.api.ContextService;
import org.xbib.concurrent.util.ee.api.ManagedTask;
import org.xbib.concurrent.util.ee.spi.ContextHandle;
import org.xbib.concurrent.util.ee.spi.ContextSetupProvider;
import org.xbib.concurrent.util.ee.spi.TransactionHandle;
import org.xbib.concurrent.util.ee.spi.TransactionSetupProvider;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * InvocationHandler used by ContextServiceImpl
 */
public class ContextProxyInvocationHandler implements InvocationHandler, Serializable {

    static final long serialVersionUID = -2887560418884002777L;
    
    final protected ContextSetupProvider contextSetupProvider;
    protected ContextService contextService;
    final protected ContextHandle capturedContextHandle;
    final protected TransactionSetupProvider transactionSetupProvider;
    final protected Object proxiedObject;
    protected Map<String, String> executionProperties;
   
    public ContextProxyInvocationHandler(ContextServiceImpl contextService, Object proxiedObject,
                                         Map<String, String> executionProperties) {
        this.contextSetupProvider = contextService.getContextSetupProvider();
        this.proxiedObject = proxiedObject;
        this.contextService = contextService;
        this.transactionSetupProvider = contextService.getTransactionSetupProvider();
        this.executionProperties = executionProperties;
        this.capturedContextHandle = 
                contextSetupProvider.saveContext(contextService, executionProperties);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result;
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        
        if (methodDeclaringClass == java.lang.Object.class) {
            // hashCode, equals, or toString method of java.lang.Object will
            // have java.lang.Object as declaring class (per java doc in
            // java.lang.reflect.Proxy). These methods would not be run
            // under creator's context
            result = method.invoke(proxiedObject, args);
        }
        else {
            // for all other methods, invoke under creator's context
            ContextHandle contextHandleForReset = contextSetupProvider.setup(capturedContextHandle);
            // Ask TransactionSetupProvider to perform any transaction related
            // setup before running the proxy. For example, suspend current
            // transaction on current thread unless TRANSACTION property is set
            // to USE_TRANSACTION_OF_EXECUTION_THREAD
            TransactionHandle txHandle = null;
            if (transactionSetupProvider != null) {
              txHandle = transactionSetupProvider.beforeProxyMethod(getTransactionExecutionProperty());
            }
            try {
                result = method.invoke(proxiedObject, args);
            }
            finally {
                contextSetupProvider.reset(contextHandleForReset);
                if (transactionSetupProvider != null) {
                    transactionSetupProvider.afterProxyMethod(txHandle, getTransactionExecutionProperty());
                }
            }
        }
        return result;
    }

    public Map<String, String> getExecutionProperties() {
        // returns a copy of the executionProperties
        if (executionProperties == null) {
            return null;
        }
        Map<String, String> copy = new HashMap<>();
        copy.putAll(executionProperties);
        return copy;
    }

    public ContextService getContextService() {
        return contextService;
    }
    
    protected String getTransactionExecutionProperty() {
      if (executionProperties != null && executionProperties.get(ManagedTask.TRANSACTION) != null) {
          return executionProperties.get(ManagedTask.TRANSACTION);
      }
      return ManagedTask.SUSPEND;
    }
    
}
