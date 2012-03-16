/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     matic
 */
package org.nuxeo.runtime.tomcat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.jar.JarFile;

import sun.misc.URLClassPath;
import sun.net.www.protocol.jar.JarURLConnection;

/**
 * @author matic
 * 
 */
public class JarFileCloser {

    protected URLClassLoader loader;

    protected ArrayList<?> loaders;

    Field jarField;

    Method getJarFileMethod;

    HashMap<?, ?> lmap;
    
    Object factory;

    Method factoryGetMethod;
    
    Method factoryCloseMethod;
    
    protected static String serializeURL(URL location) {
        StringBuilder localStringBuilder = new StringBuilder(128);
        String str1 = location.getProtocol();
        if (str1 != null) {
            str1 = str1.toLowerCase();
            localStringBuilder.append(str1);
            localStringBuilder.append("://");
        }
        String str2 = location.getHost();
        if (str2 != null) {
            str2 = str2.toLowerCase();
            localStringBuilder.append(str2);
            int i = location.getPort();
            if (i == -1)
                i = location.getDefaultPort();
            if (i != -1)
                localStringBuilder.append(":").append(i);
        }
        String str3 = location.getFile();
        if (str3 != null)
            localStringBuilder.append(str3);
        return localStringBuilder.toString();
    }

    public JarFileCloser(URLClassLoader loader)
            throws SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException,
            NoSuchMethodException, ClassNotFoundException {
        this.loader = loader;
        Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
        ucpField.setAccessible(true);

        Object ucp = ucpField.get(loader);
        Class<?> ucpClass = ucp.getClass();
        Field lmapField = ucpClass.getDeclaredField("lmap");
        lmapField.setAccessible(true);
        lmap = (HashMap<?, ?>) lmapField.get(ucp);
        Field loadersField = ucpClass.getDeclaredField("loaders");
        loadersField.setAccessible(true);
        loaders = (ArrayList<?>) loadersField.get(ucp);
        Class<?> jarLoaderClass = getJarLoaderClass();
        jarField = jarLoaderClass.getDeclaredField("jar");
        jarField.setAccessible(true);
        getJarFileMethod = jarLoaderClass.getDeclaredMethod("getJarFile",
                new Class<?>[] { URL.class });
        getJarFileMethod.setAccessible(true);
        Field jarFileFactoryField = JarURLConnection.class.getDeclaredField("factory");
        jarFileFactoryField.setAccessible(true);
        factory = jarFileFactoryField.get(null);
        Class<?> factoryClass = getFactoryClass();
        factoryGetMethod = factoryClass.getMethod("get", new Class<?>[] { URL.class });
        factoryGetMethod.setAccessible(true);
        factoryCloseMethod = factoryClass.getMethod("close", new Class<?>[] { JarFile.class });
        factoryCloseMethod.setAccessible(true);
    }

    protected static Class<?> getJarLoaderClass() {
        for (Class<?> innerClass : URLClassPath.class.getDeclaredClasses()) {
            if ("JarLoader".equals(innerClass.getSimpleName())) {
                return innerClass;
            }
        }
        throw new UnsupportedOperationException("Cannot find JarLoader class");
    }
    
    
    protected static Class<?> getFactoryClass() throws ClassNotFoundException {
        return JarFileCloser.class.getClassLoader().loadClass("sun.net.www.protocol.jar.JarFileFactory");
    }

    Object getLoader(String name) throws IllegalArgumentException,
            IllegalAccessException {
        for (Object loader : loaders) {
            JarFile jar = (JarFile) jarField.get(loader);
            if (name.equals(jar.getName())) {
                return loader;
            }
        }
        throw new IllegalArgumentException("No such jar " + name);
    }

    public boolean closeJar(URL location) throws IllegalArgumentException,
            IllegalAccessException, IOException, InvocationTargetException {
        if (lmap.isEmpty()) {
            return false;
        }
        Object firstKey = lmap.keySet().iterator().next();
        Object loader = firstKey instanceof URL ? lmap.get(location)
                : lmap.get(serializeURL(location));
        if (loader == null) {
            return false;
        }
        JarFile jar = (JarFile) jarField.get(loader);
        jarField.set(loader, null);
        jar.close();
        JarFile cachedJar  = (JarFile)factoryGetMethod.invoke(factory, new Object[] { location });
        factoryCloseMethod.invoke(factory, cachedJar);
        cachedJar.close();
        return true;
    }

}
