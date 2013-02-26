package org.apache.sshd.common.service;

import org.apache.sshd.agent.common.AgentForwardSupport;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.x11.X11ForwardSupport;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 19/02/13
 * Time: 11:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionServiceProvider extends ConnectionService<ServerSession> implements ServiceProvider {

    public static class Factory extends ServiceProviderFactory {
        public Factory() {
           super(SERVICE_NAME);
        }
        public ServiceProvider create(ServerSession session, Object sessionLock, boolean authenticated, String username) {
            if (authenticated) {
                return new ConnectionServiceProvider(session, sessionLock);
            } else {
                return null;
            }
        }
    }

    private ConnectionServiceProvider(ServerSession session, Object sessionLock) {
        super(session, sessionLock, new AgentForwardSupport(session), new X11ForwardSupport(session));
    }

    public void start() {
        // nothing to do
    }
}
