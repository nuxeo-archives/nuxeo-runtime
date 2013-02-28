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
 */
package org.nuxeo.runtime.test.runner;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.XMLLayout;

/**
 * Test feature to capture all trace in xml file.</br>
 *
 * @since 5.7
 */
public class LogTraceFeature extends SimpleFeature {

    protected Logger rootLogger = Logger.getRootLogger();

    protected Level previousLevel;

    protected Appender appender;


    @Override
    public void start(FeaturesRunner runner) throws Exception {
        appender = new FileAppender(new XMLLayout(), "target/trace.xml");
        rootLogger.addAppender(appender);
        previousLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.ALL);
        super.start(runner);
    }


    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        super.stop(runner);
        appender = new FileAppender(new XMLLayout(), "target/trace.xml");
        rootLogger.removeAppender(appender);
        rootLogger.setLevel(previousLevel);
        appender = null;
    }
}
