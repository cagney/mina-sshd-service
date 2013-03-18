package org.apache.sshd.common.service;

import org.apache.sshd.common.NameMap;
import org.apache.sshd.server.session.ServerSession;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 19/02/13
 * Time: 11:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionServiceProvider extends ConnectionService<ServerSession> implements ServiceProvider {

    /**
     * Implement the official ssh-connection protocol.
     * @param session
     * @param sessionLock
     */
    ConnectionServiceProvider(ServerSession session, Object sessionLock) {
        this(SERVICE_NAME, session, sessionLock);
    }

    /**
     * Implement a custom ssh-connection like protocol.
     * @param serviceName
     * @param session
     * @param sessionLock
     */
    protected ConnectionServiceProvider(String serviceName, ServerSession session, Object sessionLock) {
        super(serviceName, session, sessionLock);
    }

    public void start() {
        // nothing to do
    }
}
