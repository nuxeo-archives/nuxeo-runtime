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

import static org.junit.Assert.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.junit.Assert;
import org.junit.runners.model.FrameworkMethod;
import org.nuxeo.common.logging.JavaUtilLoggingHelper;

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
@Features(LogTraceFeature.class)
public class LogCaptureFeature extends SimpleFeature {

    protected class UncaughtErrorsAppender extends AppenderSkeleton {

        @Override
        public boolean requiresLayout() {
            return false;
        }

        @Override
        public void close() {

        }

        @Override
        protected void append(LoggingEvent event) {
            myResult.uncaughtErrors.add(event);
        }
    }

    protected class ExcludeAppender extends AppenderSkeleton {
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
    }

    protected class IncludeAppender extends AppenderSkeleton {

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
    }

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
        Class<? extends LogCaptureFeature.Filter> value() default LogCaptureFeature.Filter.Closed.class;

        /**
         * Filter appended events
         */
        boolean additivity() default false;

        /**
         * Filter these categories
         */
        Class<?>[] loggers() default LogCaptureFeature.class;

    }

    public class Result {

        public final ArrayList<LoggingEvent> caughtEvents = new ArrayList<LoggingEvent>();

        public final ArrayList<LoggingEvent> uncaughtErrors = new ArrayList<LoggingEvent>();

        protected boolean noFilterFlag = false;

        protected void checkNoFilter() {
            if (noFilterFlag) {
                throw new LogCaptureFeature.NoFilterError();
            }
        }

        public void assertHasEvent() throws NoFilterError {
            checkNoFilter();
            Assert.assertFalse("No log result found", caughtEvents.isEmpty());
        }

        public void assertHasNoUncaughtErrors() {
            checkNoFilter();
            assertEquals("uncaught errors, should be filtered through log capture with annotion", 0, uncaughtErrors.size());
        }

        public void assertContains(String... fragments) {
            checkNoFilter();

            Iterator<LoggingEvent> events = caughtEvents.iterator();

            for (String fragment:fragments) {
                LoggingEvent event = events.next();
                if (!((String) event.getMessage()).contains(fragment)) {
                    Assert.fail(fragment + " not found in caught exception");
                }
            }

        }

        public void clear() {
            caughtEvents.clear();
            uncaughtErrors.clear();
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

        public class Closed implements Filter {

            @Override
            public boolean accept(LoggingEvent event) {
                return false;
            }

        }

        public class Errors implements Filter {

            @Override
            public boolean accept(LoggingEvent event) {
                return event.getLevel().isGreaterOrEqual(Level.ERROR);
            }

        };

        public class WarnAndErrors implements Filter {

            @Override
            public boolean accept(LoggingEvent event) {
                return event.getLevel().isGreaterOrEqual(Level.WARN);
            }

        };
    }

    protected Filter filter;

    protected final Result myResult = new Result();

    protected final Appender caughtAppender = new IncludeAppender();

    protected final Appender uncaughtErrorsAppender = new UncaughtErrorsAppender();

    @Override
    public void configure(FeaturesRunner runner, com.google.inject.Binder binder) {
        binder.bind(Result.class).toInstance(myResult);
    };

    @Override
    public void start(FeaturesRunner runner) throws Exception {
        JavaUtilLoggingHelper.redirectToApacheCommons(java.util.logging.Level.INFO);
        caughtAppender.addFilter(ACCEPT_FILTERED);
        uncaughtErrorsAppender.addFilter(ACCEPT_UNCAUGHT_ERRORS);
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        JavaUtilLoggingHelper.reset();
    }

    protected With with;

    protected final Set<LoggerContext> alteredLoggers = new HashSet<LoggerContext>();

    protected class LoggerContext {
        final Logger logger;

        final boolean additivity;

        final Appender console;
        LoggerContext(Logger instance) {
            logger = instance;
            additivity = instance.getAdditivity();
            console = instance.getAppender("CONSOLE");
        }

    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {

        with = runner.getConfig(method, With.class);
        Class<? extends Filter> filterClass = with.value();
        filter = filterClass.newInstance();
        configureLoggers();
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {
        try {
            myResult.assertHasNoUncaughtErrors();
        } finally {
            restoreLoggers();
            filter = null;
            myResult.clear();
        }
    }

    protected void configureLoggers() {
        Logger rootLogger = Logger.getRootLogger();
        for (Class<?> category : with.loggers()) {
            Logger logger = category.isAssignableFrom(LogCaptureFeature.class) ? rootLogger
                    : Logger.getLogger(category);
            configureLogger(logger);
        }
        rootLogger.addAppender(uncaughtErrorsAppender);
    }

    protected ConsoleAppender getConsoleAppender(Logger logger) {
        Enumeration<Appender> e = logger.getAllAppenders();
        while (e.hasMoreElements()) {
            Appender a = e.nextElement();
            if (a instanceof ConsoleAppender) {
                return (ConsoleAppender)a;
            }
        }
        return null;
    }

    protected class ConsoleInvocationHandler implements  InvocationHandler {

        protected final Appender wrapped;

         protected ConsoleInvocationHandler(Appender object) {
            wrapped = object;
        }


        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if ("doAppend".equals(method.getName())) {
                if (filter.accept((LoggingEvent)args[0])) {
                    return null;
                }
            }
            return method.invoke(wrapped, args);
        }

    }

    protected Appender wrapAppender(Appender appender) {
        InvocationHandler handler = new ConsoleInvocationHandler(appender);
        return Appender.class.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[] { Appender.class,  OptionHandler.class }, handler));
    }

    protected Appender unwrapAppender(Appender appender) {
       return ((ConsoleInvocationHandler)Proxy.getInvocationHandler(appender)).wrapped;
    }

    protected void configureLogger(Logger logger) {
        LoggerContext context = new LoggerContext(logger);
        if (context.console != null) {
            logger.removeAppender("CONSOLE");
            logger.addAppender(wrapAppender(context.console));
        }
        logger.addAppender(caughtAppender);
        logger.setAdditivity(false);
        alteredLoggers.add(context);
    }

    protected void restoreLoggers() {
        Logger rootLogger = Logger.getRootLogger();
        rootLogger.removeAppender(uncaughtErrorsAppender);
        for (LoggerContext context:alteredLoggers) {
            restoreLogger(context);
        }
    }

    protected void restoreLogger(LoggerContext context) {
        context.logger.removeAppender(caughtAppender);
        if (context.console != null) {
            context.logger.removeAppender("CONSOLE");
            context.logger.addAppender(context.console);
        }
        context.logger.setAdditivity(context.additivity);
    }

    protected final org.apache.log4j.spi.Filter ACCEPT_FILTERED = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            return filter.accept(event) ? ACCEPT : DENY;
        }

    };

    protected final org.apache.log4j.spi.Filter ACCEPT_UNCAUGHT = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            return filter.accept(event) ? DENY : ACCEPT;
        }

    };

    protected final org.apache.log4j.spi.Filter ACCEPT_UNCAUGHT_ERRORS = new org.apache.log4j.spi.Filter() {

        @Override
        public int decide(LoggingEvent event) {
            if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) {
                return DENY;
            }
            if (myResult.caughtEvents.contains(event)) {
                return DENY;
            }
            return ACCEPT;
        }

    };

}
