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
package org.nuxeo.runtime.tomcat.jar;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;


/**
 *
 * Given a location, close the corresponding jar files opened by URL class loaders and in jar file cache
 *
 * @since 5.6
 * @author matic
 *
 */
public class JarFileCloser {

    protected static  JarFileCloser INSTANCE;

    public static void initialize(URLClassLoader sharedResourcesCL, URLClassLoader webappCL) {
        INSTANCE = new JarFileCloser(sharedResourcesCL, webappCL);
    }

    public static void cleanup() {
        INSTANCE = null;
    }

    protected URLClassLoaderCloser sharedResourcesCloser;

    protected URLClassLoaderCloser webappCloser;

    protected Map<URLClassLoader,URLClassLoaderCloser> urlClassLoderClosers =
            new HashMap<URLClassLoader,URLClassLoaderCloser>();

    protected JarFileFactoryCloser factoryCloser = new JarFileFactoryCloser();


    protected JarFileCloser(URLClassLoader sharedResourcesCL, URLClassLoader webappCL)  {
        sharedResourcesCloser = new URLClassLoaderCloser(sharedResourcesCL);
        webappCloser = new URLClassLoaderCloser(webappCL);
        factoryCloser = new JarFileFactoryCloser();
    }


    protected  void doClose(URL location) throws IOException {
       if (sharedResourcesCloser.close(location) == false) {
           webappCloser.close(location);
       }
       factoryCloser.close(location);
    }

    public static void close(File file) throws IOException {
        INSTANCE.doClose(file.toURI().toURL());
    }

}
