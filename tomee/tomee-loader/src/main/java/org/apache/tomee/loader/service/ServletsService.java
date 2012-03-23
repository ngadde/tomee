/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tomee.loader.service;

import org.apache.openejb.BeanContext;
import org.apache.openejb.core.ivm.BaseEjbProxyHandler;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.openejb.util.proxy.ProxyManager;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ServletsService {
    private final Context ctx;

    public void close() {
        if (this.ctx == null) {
            return; //do nothing
        }

        try {
            this.ctx.close();
        } catch (NamingException e) {
            //do nothing
        }
    }

    public ServletsService() {
        Context ctx = null;
        {
            final Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.core.LocalInitialContextFactory");
            properties.put("openejb.loader", "embed");
            try {
                ctx = new InitialContext(properties);
            } catch (NamingException e) {
                //do nothing
            }
        }
        this.ctx = ctx;
    }


    public List<Map<String, Object>> getJndi(String path) throws NamingException {
        final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        if (this.ctx == null) {
            return Collections.emptyList(); //do nothing
        }
        mountJndiList(result, this.ctx, path);
        return result;
    }

    private void mountJndiList(List<Map<String, Object>> jndi, Context context, String root) throws NamingException {
        final NamingEnumeration namingEnumeration;
        try {
            namingEnumeration = context.list(root);
        } catch (NamingException e) {
            //not found?
            return;
        }
        while (namingEnumeration.hasMoreElements()) {
            final NameClassPair pair = (NameClassPair) namingEnumeration.next();
            final String key = root + "/" + pair.getName();
            final Object obj;
            try {
                obj = context.lookup(key);
            } catch (NamingException e) {
                //not found?
                continue;
            }

            if (Context.class.isInstance(obj)) {
                mountJndiList(jndi, Context.class.cast(obj), key);
            } else {
                final Map<String, Object> dto = new HashMap<String, Object>();
                dto.put("path", key);
                dto.put("name", pair.getName());
                dto.put("value", getStr(obj));

                jndi.add(dto);
            }
        }
    }

    private void populateClassList(List<String> list, List<Class> classes) {
        if (classes == null) {
            return;
        }
        for (Class<?> cls : classes) {
            list.add(getStr(cls));
        }
    }

    private BeanContext getDeployment(String deploymentID) {
        ContainerSystem containerSystem = SystemInstance.get().getComponent(ContainerSystem.class);
        BeanContext ejb = containerSystem.getBeanContext(deploymentID);
        return ejb;
    }

    private String getDeploymentId(Object ejbObj) throws NamingException {
        final BaseEjbProxyHandler handler = (BaseEjbProxyHandler) ProxyManager.getInvocationHandler(ejbObj);
        return getStr(handler.deploymentID);
    }

    private String getStr(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
