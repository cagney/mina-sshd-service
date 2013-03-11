package org.apache.sshd.common.service;

import org.apache.sshd.server.session.ServerSession;

/**
* Created with IntelliJ IDEA.
* User: cagney
* Date: 11/03/13
* Time: 2:56 PM
* To change this template use File | Settings | File Templates.
*/
public class UserAuthServiceProviderFactory extends ServiceProviderFactory {
    public UserAuthServiceProviderFactory() {
        super(UserAuthService.SERVICE_NAME);
    }

    public ServiceProvider create(ServerSession session, Object sessionLock, boolean authenticated, String username) {
        if (authenticated) {
            return null;
        } else {
            return new UserAuthServiceProvider(session, sessionLock);
        }
    }
}
