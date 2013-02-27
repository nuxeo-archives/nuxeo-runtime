package org.nuxeo.runtime.datasource.pool;

import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;

public interface ConnectionPoolMXBean extends ConnectionPoolMBean {

    int getMaxInUse();

}
