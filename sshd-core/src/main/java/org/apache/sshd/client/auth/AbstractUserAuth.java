package org.apache.sshd.client.auth;

import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.service.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 12/03/13
 * Time: 1:11 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractUserAuth {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final ClientSessionImpl session;
    protected final ServiceClient service;
    protected final String serviceName;
    protected final String username;

    protected AbstractUserAuth(ClientSessionImpl session, ServiceClient service, String username) {
        this.session = session;
        this.username = username;
        this.service = service;
        this.serviceName = service.getName(); // make life easier
    }

    public String getUsername() {
        return this.username;
    }

    public ServiceClient getService() {
        return this.service;
    }
}
