package org.apache.sshd.client.auth;

import org.apache.sshd.client.session.ClientSessionImpl;
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
    protected final String serviceName;
    protected final String username;

    protected AbstractUserAuth(ClientSessionImpl session, String serviceName, String username) {
        this.session = session;
        this.username = username;
        this.serviceName = serviceName;
    }

    public String getUsername() {
        return username;
    }

    public String getServiceName() {
        return serviceName;
    }
}
