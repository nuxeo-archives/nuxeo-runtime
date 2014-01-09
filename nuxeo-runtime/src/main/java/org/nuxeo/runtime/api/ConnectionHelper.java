/*
 * (C) Copyright 2012-2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.runtime.api;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * This helper provides a way to get a JDBC connection, through
 * {@link #getConnection(String)}, that will return a connection wrapper able to
 * use a shared connection when used in transactional mode and
 * setAutoCommit(false) is called, and otherwise use a normal physical JDBC
 * connection.
 * <p>
 * The physical connections are created from the datasource configured using the
 * framework property {@value #SINGLE_DS}.
 * <p>
 * This helper is used to implement consistent resource management in a non-XA
 * context. Several users of the shared connection can call setAutoCommit(false)
 * then do transactional work and commit(). Only the commit() of the last user
 * will do an actual commit on the physical connection.
 *
 * @since 5.7
 * @deprecated
 */
@Deprecated
public class ConnectionHelper {

    private static final Log log = LogFactory.getLog(ConnectionHelper.class);


    /**
     * Property holding a datasource name to use to replace all database
     * accesses.
     */
    public static final String SINGLE_DS = "nuxeo.db.singleDataSource";

    public static final int MAX_CONNECTION_TRIES = 3;

    /**
     * Tries to unwrap the connection to get the real physical one (returned by
     * the original datasource).
     * <p>
     * This should only be used by code that needs to cast the connection to a
     * driver-specific class to use driver-specific features.
     *
     * @throws SQLException if no actual physical connection was allocated yet
     */
    public static Connection unwrap(Connection connection) throws SQLException {
        return connection;
    }


    /**
     * Checks if single transaction-local datasource mode will be used for the
     * given datasource name.
     *
     * @return {@code true} if using a single transaction-local connection for
     *         this datasource
     */
    public static boolean useSingleConnection(String dataSourceName) {
        return true;
    }

    /**
     * Gets the fake name we use to pass to ConnectionHelper.getConnection, in
     * order for exclusions on these connections to be possible.
     */
    public static String getPseudoDataSourceNameForRepository(
            String repositoryName) {
        return "repository_" + repositoryName;
    }

    /**
     * Gets a new reference to the transaction-local JDBC connection for the
     * given dataSource. The connection <strong>MUST</strong> be closed in a
     * finally block when code is done using it.
     * <p>
     * If the passed dataSource name is in the exclusion list, null will be
     * returned.
     *
     * @param dataSourceName the datasource for which the connection is
     *            requested
     * @return a new reference to the connection, or {@code null} if single
     *         datasource connection sharing is not in effect
     */
    public static Connection getConnection(String dataSourceName)
            throws SQLException {
        return getConnection(dataSourceName, false);
    }

    /**
     * Gets a new reference to the transaction-local JDBC connection for the
     * given dataSource. The connection <strong>MUST</strong> be closed in a
     * finally block when code is done using it.
     * <p>
     * If the passed dataSource name is in the exclusion list, null will be
     * returned.
     * <p>
     * If noSharing is requested, the connection will never come from the
     * transaction-local and will always be newly allocated.
     *
     * @param dataSourceName the datasource for which the connection is
     *            requested
     * @param noSharing {@code true} if this connection must not be shared with
     *            others
     * @return a new reference to the connection, or {@code null} if single
     *         datasource connection sharing is not in effect
     */
    public static Connection getConnection(String dataSourceName,
            boolean noSharing) throws SQLException {
        return getPhysicalConnection(dataSourceName);
    }

    private static Connection getPhysicalConnection() throws SQLException {
        return getPhysicalConnection(Framework.getProperty(SINGLE_DS));
    }

    /**
     * Gets a physical connection from a datasource name.
     * <p>
     * A few retries are done to work around databases that have problems with
     * many open/close in a row.
     *
     * @param dataSourceName the datasource name
     * @return the connection
     */
    private static Connection getPhysicalConnection(String dataSourceName)
            throws SQLException {
        DataSource dataSource = getDataSource(dataSourceName);
        for (int tryNo = 0;; tryNo++) {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                if (tryNo >= MAX_CONNECTION_TRIES) {
                    throw e;
                }
                if (e.getErrorCode() != 12519) {
                    throw e;
                }
                // Oracle: Listener refused the connection with the
                // following error: ORA-12519, TNS:no appropriate
                // service handler found SQLState = "66000"
                // Happens when connections are open too fast (unit tests)
                // -> retry a few times after a small delay
                if (log.isDebugEnabled()) {
                    log.debug(String.format(
                            "Connections open too fast, retrying in %ds: %s",
                            Integer.valueOf(tryNo),
                            e.getMessage().replace("\n", " ")));
                }
                try {
                    Thread.sleep(1000 * tryNo);
                } catch (InterruptedException ie) {
                    // restore interrupted status
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Gets a datasource from a datasource name, or in test mode use test
     * connection parameters.
     *
     * @param dataSourceName the datasource name
     * @return the datasource
     */
    private static DataSource getDataSource(String dataSourceName)
            throws SQLException {
        try {
            return DataSourceHelper.getDataSource(dataSourceName);
        } catch (NamingException e) {
            if (Framework.isTestModeSet()) {
                String url = Framework.getProperty("nuxeo.test.vcs.url");
                String user = Framework.getProperty("nuxeo.test.vcs.user");
                String password = Framework.getProperty("nuxeo.test.vcs.password");
                if (url != null && user != null) {
                    return new DataSourceFromUrl(url, user, password); // driver?
                }
            }
            throw new SQLException("Cannot find datasource: " + dataSourceName,
                    e);
        }
    }

    /**
     * Checks how many references there are to shared connections.
     * <p>
     * USED IN UNIT TESTS OR FOR DEBUGGING.
     */
    public static int countConnectionReferences() {
        return 0;
    }

    /**
     * Clears the remaining connection references for the current thread.
     * <p>
     * USED IN UNIT TESTS ONLY.
     */
    public static void clearConnectionReferences() {
        ;
    }

    /**
     * If sharing is in effect, registers a synchronization with the current
     * transaction, making sure it runs before the
     * {@link SharedConnectionSynchronization}.
     *
     * @return {@code true}
     */
    public static boolean registerSynchronization(Synchronization sync)
            throws SystemException, NamingException, RollbackException {
        TransactionHelper.lookupTransactionManager().getTransaction().registerSynchronization(sync);
        return true;
    }

    /**
     * If sharing is in effect, registers a synchronization with the current
     * transaction, making sure the {@link Synchronization#afterCompletion}
     * method runs after the {@link SharedConnectionSynchronization}.
     *
     * @return {@code true}
     */
    public static boolean registerSynchronizationLast(Synchronization sync)
            throws SystemException, NamingException {
        TransactionHelper.lookupTransactionSynchronizationRegistry().registerInterposedSynchronization(
                sync);
        return true;
    }

}
