package org.apache.sshd.common.service;

import org.apache.sshd.server.session.ServerSession;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 22/02/13
 * Time: 2:42 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ServiceProviderFactory extends ServiceFactory {

    protected ServiceProviderFactory(String name) {
        super(name);
    }

    public abstract ServiceProvider create(ServerSession session, Object sessionLock, boolean authenticated, String username);

}
