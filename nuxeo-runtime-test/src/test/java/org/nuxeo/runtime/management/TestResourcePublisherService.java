/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.runtime.management;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.junit.Ignore;
import org.junit.Test;
import org.nuxeo.runtime.api.Framework;

public class TestResourcePublisherService extends ManagementTestCase {

    @Test
    public void testRegisteredService() throws Exception {
        assertNotNull(Framework.getService(ResourcePublisher.class));
    }

    @Test
    public void testRegisterResource() {
        publisherService.registerResource("dummy", "org.nuxeo:name=dummy",
                DummyMBean.class, new DummyService());
        publisherService.bindResources();
        Set<ObjectName> registeredNames = doQuery("org.nuxeo:name=dummy");
        assertNotNull(registeredNames);
        assertEquals(1, registeredNames.size());
    }

    @Test
    @Ignore
    public void testRegisterFactory() throws Exception {
        ResourceFactoryDescriptor descriptor = new ResourceFactoryDescriptor(
                DummyFactory.class);
        publisherService.registerContribution(descriptor, "factories", null);
        Set<ObjectName> registeredNames = doQuery("org.nuxeo:name=dummy");
        assertNotNull(registeredNames);
        assertEquals(registeredNames.size(), 1);
    }

    @Test
    public void testServerLocator() throws Exception {
        MBeanServer testServer = MBeanServerFactory.createMBeanServer("test");
        ObjectName testName = new ObjectName("test:test=test");
        publisherService.bindForTest(testServer, testName, new DummyService(), DummyMBean.class);
        publisherService.bindResources();
        locatorService.registerLocator("test", true);
        MBeanServer locatedServer = locatorService.lookupServer(testName);
        assertNotNull(locatedServer);
        assertTrue(locatedServer.isRegistered(testName));
    }

    @Test
    public void testXMLConfiguration() throws Exception {
        Set<String> shortcutsName = publisherService.getShortcutsName();
        int size = shortcutsName.size();
        deployTestContrib(OSGI_BUNDLE_NAME, "management-tests-service.xml");
        deployTestContrib(OSGI_BUNDLE_NAME, "management-tests-contrib.xml");

        publisherService.bindResources();
        String qualifiedName = ObjectNameFactory.formatTypeQuery("service");

        Set<ObjectName> registeredNames = doQuery(qualifiedName);
        assertNotNull(registeredNames);
        assertEquals(4, registeredNames.size());

        shortcutsName = publisherService.getShortcutsName();
        assertNotNull(shortcutsName);
        assertEquals(size+4, shortcutsName.size());
        assertTrue(shortcutsName.contains("dummy"));
    }

}
