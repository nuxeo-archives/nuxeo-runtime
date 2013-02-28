package org.nuxeo.runtime.datasource;

import java.sql.SQLException;

public interface DatabaseStarter {

    void start() throws SQLException;

    void stop() throws SQLException;

}
