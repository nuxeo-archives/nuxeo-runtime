/*******************************************************************************
 * Copyright (c) 2013 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *******************************************************************************/
package org.nuxeo.runtime.datasource.h2;

import java.net.Socket;
import java.sql.SQLException;

import org.h2.tools.Server;
import org.h2.util.NetUtils;
import org.nuxeo.runtime.datasource.DatabaseStarter;

public class DatabaseH2Starter implements DatabaseStarter
{

    protected final int port;

    protected Server server;

    public DatabaseH2Starter(int port) {
        this.port = port;
    }

    @Override
    public void start() throws SQLException {
        server = Server.createTcpServer("-tcpPort", Integer.toString(port));
        server.start();
    }

    @Override
    public void stop() throws SQLException {
        server.stop();
    }


    public boolean isStarted() throws SQLException {
        try {
            Socket s = NetUtils.createLoopbackSocket(port, false);
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
