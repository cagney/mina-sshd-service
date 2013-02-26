package org.apache.sshd.common.service;

import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.future.CloseFuture;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 22/02/13
 * Time: 12:37 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ServiceClientsFactory {

    /**
     * Create the lists of clients that will be used during the session.
     *
     * Presumably the first is UserAuthService followed by ConnectionService
     * @param session
     * @param sessionLock
     * @param closeFuture
     * @return
     */
    public abstract LinkedList<ServiceClient> create(ClientSessionImpl session, Object sessionLock, CloseFuture closeFuture);

}
