package org.apache.sshd.common.service;

import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.NameMap;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 25/02/13
 * Time: 10:48 AM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultServiceClientsFactory extends ServiceClientsFactory {
    @Override
    public LinkedList<ServiceClient> create(ClientSessionImpl session, Object sessionLock) {
        LinkedList<ServiceClient> serviceClients = new LinkedList<ServiceClient>();
        serviceClients.add(new UserAuthServiceClient(session, sessionLock));
        serviceClients.add(new ConnectionServiceClient(session, sessionLock));
        return serviceClients;
    }
}
