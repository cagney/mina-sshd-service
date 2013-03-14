package org.apache.sshd.common.service;

import org.apache.sshd.common.Name;
import org.apache.sshd.server.session.ServerSession;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 22/02/13
 * Time: 2:42 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ServiceProviderFactory extends Name {

    protected ServiceProviderFactory(String name) {
        super(name);
    }

    public abstract ServiceProvider create(ServerSession session, Object sessionLock, boolean authenticated, String username);

}
