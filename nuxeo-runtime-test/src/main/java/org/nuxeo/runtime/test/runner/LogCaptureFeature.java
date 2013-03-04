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
 *     Sun Seng David TAN <stan@nuxeo.com>
 */
package org.nuxeo.runtime.test.runner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Assert;
import org.junit.runners.model.FrameworkMethod;

import com.google.inject.Inject;

/**
 * Test feature to capture from a log4j appender to check that some log4j calls
 * have been correctly called.</br>
 *
 * On a test class or a test method using this feature, a custom
 * {@link LogCaptureFeature.Filter} class is to be provided with the annotation
 * {@link LogCaptureFeature.With} to select the log events to capture.</br>
 *
 * A {@link LogCaptureFeature.Result} instance is to be injected with
 * {@link Inject} as an attribute of the test.</br>
 *
 * The method {@link LogCaptureFeature.Result#assertHasEvent()} can then be
 * called from test methods to check that matching log calls (events) have been
 * captured.
 *
 * @since 5.7
 */
public class LogCaptureFeature extends SimpleFeature {

    public class NoFilterError extends Error {

        private static final long serialVersionUID = 1L;

    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    public @interface With {
        /**
         * Custom implementation of a filter to select event to capture.
         */
        Class<? extends LogCaptureFeature.Filter> value() default LogCaptureFeature.Filter.Open.class;

        /**
         * Filter appended events
         */
        boolean additivity() default false;

        /**
         * Filter these categories
         */
        Class<?>[] includes() default LogCaptureFeature.class;

        /**
         * Excludes these categories
         */
        Class<?>[] excludes() default LogCaptureFeature.class;
    }

    public class Result {

        protected final ArrayList<LoggingEvent> caughtEvents = new ArrayList<LoggingEvent>();

        protected boolean noFilterFlag = false;

        public void assertHasEvent() throws NoFilterError {
            if (noFilterFlag) {
                throw new LogCaptureFeature.NoFilterError();
            }
            Assert.assertFalse("No log result found", caughtEvents.isEmpty());
        }

        public void clear() {
            caughtEvents.clear();
            noFilterFlag = false;
        }

        public List<LoggingEvent> getCaughtEvents() {
            return caughtEvents;
        }

    }

    public interface Filter {
        /**
         * {@link LogCaptureFeature} will capture the event if it does match the
         * implementation condition.
         */
        boolean accept(LoggingEvent event);

        class Open implements Filter {

            @Override
            public boolean accept(LoggingEvent event) {
                return true;
            }

        }
    }

    protected Filter filter = DEFAULT_FILTER;

    protected static final Filter DEFAULT_FILTER = new Filter() {

        @Override
        public boolean accept(LoggingEvent event) {
            return true;
        }

    };

    protected final Result myResult = new Result();

    protected Appender includeAppender = new AppenderSkeleton() {

        @Override
        public boolean requiresLayout() {
            return false;
        }

        @Override
        public void close() {
        }

        @Override
        protected void append(LoggingEvent event) {
            myResult.caughtEvents.add(event);
        }

    };

    protected Appender excludeAppender = new AppenderSkeleton() {

        @Override
        public boolean requiresLayout() {
            return false;
        }

        @Override
        public void close() {

        }

        @Override
        protected void append(LoggingEvent event) {

        }
    };

    @Override
    public void configure(FeaturesRunner runner, com.google.inject.Binder binder) {
        binder.bind(Result.class).toInstance(myResult);
        includeAppender.addFilter(ACCEPT);
        excludeAppender.addFilter(DENY);
    };

    protected With with;

    protected final Map<String, LoggerContext> altered = new HashMap<String, LoggerContext>();

    protected class LoggerContext {
        final Logger logger;

        final Appender appender;

        final boolean additivity;

        final Map<Appender, org.apache.log4j.spi.Filter> filters = new HashMap<Appender, org.apache.log4j.spi.Filter>();

        LoggerContext(Logger instance, Appender added) {
            logger = instance;
            additivity = instance.getAdditivity();
            appender = added;
        }

    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {

        with = runner.getConfig(method, With.class);
        if (with == null) {
            return;
        }
        Class<? extends Filter> filterClass = with.value();
        filter = filterClass.newInstance();
        configureLoggers(with.includes(), includeAppender, with.additivity());
        configureLoggers(with.excludes(), excludeAppender, false);
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {
        if (filter == null) {
            return;
        }
        myResult.clear();
        restoreLoggers();
    }

    protected void configureLoggers(Class<?>[] categories, Appender appender,
            boolean additivity) {
        for (Class<?> category : categories) {
            Logger logger = category.isAssignableFrom(LogCaptureFeature.class) ? Logger.getRootLogger()
                    : Logger.getLogger(category);
            configureLogger(logger, appender, additivity);
        }
    }

    protected void configureLogger(Logger logger, Appender appender,
            boolean additivity) {
        String category = logger.getName();
        if (altered.containsKey(category)) {
            return;
        }
        LoggerContext context = new LoggerContext(logger, appender);
        @SuppressWarnings("unchecked")
        Enumeration<Appender> e = logger.getAllAppenders();
        while (e.hasMoreElements()) {
            Appender a = e.nextElement();
            context.filters.put(a, a.getFilter());
            a.clearFilters();
            a.addFilter(NOT_ACCEPT);
        }
        logger.addAppender(appender);
        logger.setAdditivity(additivity);
        altered.put(category, context);
    }


     protected void restoreLoggers() {
        for (Class<?> clazz : with.includes()) {
            Logger logger = clazz.isAssignableFrom(LogCaptureFeature.class) ? Logger.getRootLogger()
                    : Logger.getLogger(clazz);
            restoreLogger(logger);
        }
    }

    protected void restoreLogger(Logger logger) {
        LoggerContext context = altered.remove(logger.getName());
        logger.removeAppender(context.appender);
        @SuppressWarnings("unchecked")
        Enumeration<Appender> e = logger.getAllAppenders();
        while (e.hasMoreElements()) {
            Appender a = e.nextElement();
            a.clearFilters();
            org.apache.log4j.spi.Filter last = context.filters.get(a);
            if (last != null) {
                a.addFilter(last);
            }
        }
        logger.setAdditivity(context.additivity);
    }

    protected static final org.apache.log4j.spi.Filter DENY = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            return DENY;
        }

    };


    protected final org.apache.log4j.spi.Filter ACCEPT = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            return filter.accept(event) ? ACCEPT : DENY;
        }

    };


    protected final org.apache.log4j.spi.Filter NOT_ACCEPT = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            return filter.accept(event) ? DENY : ACCEPT;
        }

    };




}
