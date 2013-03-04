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
 *     jcarsique
 */
package org.nuxeo.connect.update.standalone;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.connect.update.PackageException;
import org.nuxeo.connect.update.PackageUpdateService;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

import com.google.inject.Binder;

public class PackageUpdateFeature extends SimpleFeature {

    protected static final Log log = LogFactory.getLog(PackageUpdateFeature.class);

    protected PackageUpdateService service;

    /**
     * Calls {@link #setupService()} to setup the service
     *
     * @see #setService(PackageUpdateService)
     * @see #setupService()
     */
    @Override
    public void start(FeaturesRunner runner) throws Exception {
        setupService();
    }

    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        binder.bind(PackageUpdateService.class).toInstance(service);
    }
    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        if (service instanceof StandaloneUpdateService) {
            tearDownStandaloneUpdateService();
        }
    }

    /**
     * Default implementation sets a {@link StandaloneUpdateService}
     *
     * @throws IOException
     * @throws PackageException
     */
    protected void setupService() throws IOException, PackageException {
        File tmpHome = File.createTempFile("tmphome", null);
        FileUtils.forceDelete(tmpHome);
        tmpHome.mkdirs();
        Environment env = new Environment(tmpHome);
        env.init();
        service = new StandaloneUpdateService(env);
        service.initialize();
    }

    protected void tearDownStandaloneUpdateService() {
        FileUtils.deleteQuietly(Environment.getDefault().getHome());
    }

}
