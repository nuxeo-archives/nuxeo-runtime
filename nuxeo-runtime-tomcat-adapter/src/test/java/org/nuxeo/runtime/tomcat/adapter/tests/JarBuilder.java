package org.nuxeo.runtime.tomcat.adapter.tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

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
public class JarBuilder {

    ArrayList<File> builtFiles = new ArrayList<File>();

    public URL buildFirst() throws FileNotFoundException, IOException {
        File file = File.createTempFile("test", ".jar");
        builtFiles.add(file);
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            writeEntry(output, "META-INF/MANIFEST.MF");
            writeEntry(output,
                    "org/nuxeo/runtime/tomcat/adapter/tests/JarBuilder.class");
            writeEntry(output, "first.marker");
        } finally {
            output.close();
        }
        return file.toURI().toURL();
    }

    public URL buildOther() throws FileNotFoundException, IOException {
        File file = File.createTempFile("test", ".jar");
        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        try {
            writeEntry(output, "META-INF/MANIFEST.MF");
            writeEntry(output,
                    "org/nuxeo/runtime/tomcat/adapter/tests/TestClassLoaderMetaIndex.class");
            writeEntry(output, "other.marker");
        } finally {
            output.close();
        }
        return file.toURI().toURL();
    }

    protected void writeEntry(JarOutputStream output, String path)
            throws IOException {
        output.putNextEntry(new ZipEntry(path));
        InputStream input = getClass().getResourceAsStream("/"+path);
        try {
            while (input.available() > 0) {
                output.write(input.read());
            }
        } finally {
            input.close();
        }
    }

    public void deleteBuiltFiles() {
        Iterator<File> iterator = builtFiles.iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            file.delete();
            iterator.remove();
        }
    }
}
