package org.apache.sshd.common.service;

import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Useful class for building out services.
 */
public abstract class AbstractService<T extends AbstractSession> implements Service {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * The service has access to and uses the sessionLock to avoid any
     * possibility of dead-lock.  For instance, the service processing an incoming message
     * simultaneous to the service processing a timer and trying to inject a message.
     */
    protected Object sessionLock;
    protected T session;
    private final String name;

    protected AbstractService(String name, T session, Object sessionLock) {
        this.name = name;
        this.session = session;
        this.sessionLock = sessionLock;
    }

    public String getName() {
        return this.name;
    }

}
