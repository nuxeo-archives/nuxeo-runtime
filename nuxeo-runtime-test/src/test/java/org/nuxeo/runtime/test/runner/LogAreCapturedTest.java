package org.nuxeo.runtime.test.runner;

import org.apache.commons.logging.LogFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(LogCaptureFeature.class)
public class LogAreCapturedTest {

	@Inject
	protected LogCaptureFeature.Result caughtEvents;

    @Test
    @Ignore
    public void shouldFail() {
    	LogFactory.getLog(LogAreCapturedTest.class).error("pfff");
    }

    @Test
    @LogCaptureFeature.With(value=LogCaptureFeature.Filter.Errors.class)
    public void shoudCaptureErrors() {
        LogFactory.getLog(LogCaptureFeature.class).error("pfff");
        caughtEvents.assertContains("pfff");
    }

    @Test
    @LogCaptureFeature.With(value=LogCaptureFeature.Filter.Errors.class, loggers=LogAreCapturedTest.class)
    public void shoudlCaptureThisClassErrors() {
        LogFactory.getLog(LogAreCapturedTest.class).error("pfff");
        caughtEvents.assertContains("pfff");
    }

    @LogCaptureFeature.With(value=LogCaptureFeature.Filter.Errors.class,loggers=LogCaptureFeature.class)
    @Test public void shouldCaptureJUL() {
    	java.util.logging.Logger.getLogger(LogAreCapturedTest.class.getName()).log(java.util.logging.Level.SEVERE, "pfff");
        caughtEvents.assertContains("pfff");
    }
}
