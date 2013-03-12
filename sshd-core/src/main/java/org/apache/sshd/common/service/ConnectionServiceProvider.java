package org.apache.sshd.common.service;

import org.apache.sshd.agent.common.AgentForwardSupport;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.x11.X11ForwardSupport;

import java.util.Map;

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
        this(SERVICE_NAME, session, sessionLock, new AgentForwardSupport(session), new X11ForwardSupport(session), GlobalRequest.defaults());
    }

    /**
     * Implement a custom ssh-connection like protocol.
     * @param serviceName
     * @param session
     * @param sessionLock
     * @param agentForwardSupport
     * @param x11ForwardSupport
     * @param globalRequestMap
     */
    protected ConnectionServiceProvider(String serviceName, ServerSession session, Object sessionLock,
                                        AgentForwardSupport agentForwardSupport, X11ForwardSupport x11ForwardSupport,
                                        Map<String,GlobalRequest> globalRequestMap) {
        super(serviceName, session, sessionLock, agentForwardSupport, x11ForwardSupport, globalRequestMap);
    }

    public void start() {
        // nothing to do
    }
}
