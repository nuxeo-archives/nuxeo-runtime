/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.runtime.transaction;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utilities to work with transactions.
 */
public class TransactionHelper {

    private static final Log log = LogFactory.getLog(TransactionHelper.class);

    private TransactionHelper() {
        // utility class
    }

    /**
     * Various binding names for the UserTransaction. They depend on the
     * application server used and how the configuration is done.
     */
    public static final String[] NAMESPACES = { "java:comp", // J2EE
            "java:comp/env", // manual binding outside appserver
            "java:", // ????
            "" // JBoss
    };

    /**
     * Looks up the User Transaction in JNDI.
     *
     * @return the User Transaction
     * @throws NamingException if not found
     */
    public static UserTransaction lookupUserTransaction()
            throws NamingException {
        return lookup(UserTransaction.class);
    }


    /**
     * Looks up the transaction synchronization registry
     *
     * @since 5.9
     */
    public static TransactionSynchronizationRegistry lookupTransactionSynchronizationRegistry() throws NamingException {
        return lookup(TransactionSynchronizationRegistry.class);
    }

    /**
     * Returns the UserTransaction JNDI binding name.
     * <p>
     * Assumes {@link #lookupUserTransaction} has been called once before.
     */
    public static String getUserTransactionJNDIName() {
        return composeName(NAMESPACES[0],UserTransaction.class.getSimpleName());
    }

    /**
     * Looks up the TransactionManager in JNDI.
     *
     * @return the TransactionManager
     * @throws NamingException if not found
     */
    public static TransactionManager lookupTransactionManager()
            throws NamingException {
        return lookup(TransactionManager.class);
    }


    protected static <T> T lookup(Class<T> type) throws NamingException {
        InitialContext dir = new InitialContext();
        String name = type.getSimpleName();
        for (int i = 0; i < NAMESPACES.length; ++i) {
            String ns = NAMESPACES[i];
            try {
                T obj = type.cast(dir.lookup(composeName(ns, name)));
                if (i != 0) {
                    synchronized (NAMESPACES) {
                        if (!ns.equals(NAMESPACES[0])) {
                            NAMESPACES[i] = NAMESPACES[0];
                            NAMESPACES[0] = ns;
                        }
                    }
                }
                return obj;
            } catch (NamingException e) {
                // try next one
            }
        }
        throw new NamingException(type.getSimpleName() + " not found in JNDI");
    }

    protected static String composeName(String ns, String name) {
        return ns.concat("/").concat(name);
    }

    /**
     * Checks if the current User Transaction is active.
     */
    public static boolean isTransactionActive() {
        try {
            return lookupUserTransaction().getStatus() == Status.STATUS_ACTIVE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the current User Transaction is marked rollback only.
     */
    public static boolean isTransactionMarkedRollback() {
        try {
            return lookupUserTransaction().getStatus() == Status.STATUS_MARKED_ROLLBACK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the current User Transaction is active or marked rollback only.
     */
    public static boolean isTransactionActiveOrMarkedRollback() {
        try {
            int status = lookupUserTransaction().getStatus();
            return status == Status.STATUS_ACTIVE
                    || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Starts a new User Transaction.
     *
     * @return {@code true} if the transaction was successfully started,
     *         {@code false} otherwise
     */
    public static boolean startTransaction() {
        UserTransaction ut;
        try {
            ut = lookupUserTransaction();
            ut.begin();
            if (log.isDebugEnabled()) {
                log.debug("Starting transaction");
            }
            return true;
        } catch (NamingException e) {
            // no transaction
        } catch (Exception e) {
            log.error("Unable to start transaction", e);
        }
        return false;
    }

    /**
     * Suspend the current transaction if active and start a new transaction
     *
     * @return the suspended transaction or null
     * @throws TransactionRuntimeException
     * @since 5.6
     */
    public static Transaction requireNewTransaction() {
        TransactionManager tm;
        try {
            tm = lookupTransactionManager();
        } catch (NamingException e) {
            return null;
        }
        try {
            Transaction tx = null;
            if (tm.getStatus() == Status.STATUS_ACTIVE) {
                tx = tm.suspend();
            }
            tm.begin();
            return tx;
        } catch (Exception e) {
            throw new TransactionRuntimeException("Cannot suspend tx", e);
        }
    }

    /**
     * Commit the current transaction if active and resume the principal
     * transaction
     *
     * @param tx
     */
    public static void resumeTransaction(Transaction tx) {
        TransactionManager mgr;
        try {
            mgr = lookupTransactionManager();
        } catch (NamingException e) {
            return;
        }
        try {
            if (mgr.getStatus() == Status.STATUS_ACTIVE) {
                mgr.commit();
            }
            if (tx != null) {
                mgr.resume(tx);
            }
        } catch (Exception e) {
            throw new TransactionRuntimeException("Cannot resume tx", e);
        }
    }

    /**
     * Starts a new User Transaction with the specified timeout.
     *
     * @param timeout the timeout in seconds, <= 0 for the default
     * @return {@code true} if the transaction was successfully started,
     *         {@code false} otherwise
     *
     * @since 5.6
     */
    public static boolean startTransaction(int timeout) {
        if (timeout < 0) {
            timeout = 0;
        }
        TransactionManager txmgr;
        try {
            txmgr = lookupTransactionManager();
        } catch (NamingException e) {
            // no transaction
            return false;
        }

        try {
            txmgr.setTransactionTimeout(timeout);
        } catch (SystemException e) {
            log.error("Unable to set transaction timeout: " + timeout, e);
            return false;
        }
        try {
            return startTransaction();
        } finally {
            try {
                txmgr.setTransactionTimeout(0);
            } catch (SystemException e) {
                log.error("Unable to reset transaction timeout", e);
            }
        }
    }

    /**
     * Commits or rolls back the User Transaction depending on the transaction
     * status.
     */
    public static void commitOrRollbackTransaction() {
        UserTransaction ut;
        try {
            ut = lookupUserTransaction();
        } catch (NamingException e) {
            log.warn("No user transaction", e);
            return;
        }
        try {
            int status = ut.getStatus();
            if (status == Status.STATUS_ACTIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Commiting transaction");
                }
                ut.commit();
            } else if (status == Status.STATUS_MARKED_ROLLBACK) {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot commit transaction because it is marked rollback only");
                }
                ut.rollback();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot commit transaction with unknown status: "
                            + status);
                }
            }
        } catch (Exception e) {
            String msg = "Unable to commit/rollback  " + ut;
            if (e instanceof RollbackException
                    && "Unable to commit: transaction marked for rollback".equals(e.getMessage())) {
                // don't log as error, this happens if there's a
                // ConcurrentModificationException at transaction end inside VCS
                log.debug(msg, e);
            } else {
                log.error(msg, e);
            }
            throw new TransactionRuntimeException(msg, e);
        }
    }

    /**
     * Sets the current User Transaction as rollback only.
     *
     * @return {@code true} if the transaction was successfully marked rollback
     *         only, {@code false} otherwise
     */
    public static boolean setTransactionRollbackOnly() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Setting transaction as rollback only");
            }
            lookupUserTransaction().setRollbackOnly();
            return true;
        } catch (NamingException e) {
            // no transaction
        } catch (Exception e) {
            log.error("Could not mark transaction as rollback only", e);
        }
        return false;
    }


}
