package org.nuxeo.runtime.tomcat.adapter.tests;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.nuxeo.runtime.tomcat.URLClassLoaderCloser;

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

/**
 * @author matic
 *
 */
public class TestClassLoaderMetaIndex {

    protected JarBuilder jarBuilder = new JarBuilder();

    @After
    public void deleteBuiltFiles() {
        jarBuilder.deleteBuiltFiles();
    }

    @Ignore
    @Test
    public void dontOpenJar() throws MalformedURLException {
        File bundles = new File("bundles");
        sun.misc.MetaIndex.registerDirectory(bundles);
        URLClassLoader cl = newClassLoader(bundles);
        URL template = cl.findResource("templates/FileOpen.ftl");
        // TODO check launchers
        assertThat(template,notNullValue());
        Assert.fail();
    }

    private URLClassLoader newClassLoader(File bundles)
            throws MalformedURLException {
        URL[] urls = new URL[] {
                new File(bundles, "classes.jar").toURI().toURL(),
                new File(bundles, "resources.jar").toURI().toURL()
        };
        URLClassLoader cl = new URLClassLoader(urls);
        return cl;
    }


    @Test
    public void canDeleteJar() throws ClassNotFoundException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, IOException {
        URL[] jarURLs = new URL[] { jarBuilder.buildFirst(), jarBuilder.buildOther() };
        URLClassLoader ucl = new URLClassLoader(jarURLs, null);
        assertThat(ucl.loadClass(TestClassLoaderMetaIndex.class.getName()), notNullValue());
        URLClassLoaderCloser fixer = new URLClassLoaderCloser(ucl);
        fixer.removeLoader(jarURLs[1]);
        File file = new File(jarURLs[1].getFile());
        assertThat(file.delete(), is(true));
        assertThat(ucl.findResource("first.marker"), notNullValue());
        assertThat(ucl.findResource("other.marker"), nullValue());
        jarBuilder.deleteBuiltFiles();
    }


}
