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

    ConnectionServiceProvider(ServerSession session, Object sessionLock, Map<String,GlobalRequest> globalRequestMap) {
        super(session, sessionLock, new AgentForwardSupport(session), new X11ForwardSupport(session), globalRequestMap);
    }

    public void start() {
        // nothing to do
    }
}
