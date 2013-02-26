package org.apache.sshd.common.service;

import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.session.ServerSession;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 21/02/13
 * Time: 1:40 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class UserAuthService<T extends AbstractSession> extends AbstractService<T> implements Service {

    public static final String SERVICE_NAME = "ssh-userauth";

    protected UserAuthService(T session, Object sessionLock) {
        super(SERVICE_NAME, session, sessionLock);
    }

}
