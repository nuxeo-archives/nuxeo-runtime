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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.jar.JarFile;

/**
 * @author matic
 *
 */
public class URLClassLoaderCloser {

    protected URLClassLoader loader;

    protected ArrayList<?> loaders;

    Field jarField;

    HashMap<URL,?> lmap;

    @SuppressWarnings("unchecked")
    public URLClassLoaderCloser(URLClassLoader loader) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        this.loader = loader;
        Field ucpField = loader.getClass().getDeclaredField("ucp");
        ucpField.setAccessible(true);

        Object ucp = ucpField.get(loader);
        Class<?> ucpClass = ucp.getClass();
        Field lmapField = ucpClass.getDeclaredField("lmap");
        lmapField.setAccessible(true);
        lmap = (HashMap<URL,?>)lmapField.get(ucp);
        Field loadersField = ucpClass.getDeclaredField("loaders");
        loadersField.setAccessible(true);
        loaders = (ArrayList<?>)loadersField.get(ucp);
        Class<?> loaderClass = loaders.get(0).getClass();
        jarField = loaderClass.getDeclaredField("jar");
        jarField.setAccessible(true);

    }

    boolean isUsed(Object loader) {
        return lmap.containsKey(loader);
    }

    Object getLoader(String name) throws IllegalArgumentException, IllegalAccessException {
        for (Object loader:loaders) {
            JarFile jar = (JarFile)jarField.get(loader);
            if (name.equals(jar.getName())) {
                return loader;
            }
        }
        throw new IllegalArgumentException("No such jar " + name);
    }

    public void removeLoader(URL location) throws IllegalArgumentException, IllegalAccessException, IOException {
        Object loader = lmap.remove(location);
        loaders.remove(loader);
        JarFile jar = (JarFile) jarField.get(loader);
        jar.close();
    }

}
