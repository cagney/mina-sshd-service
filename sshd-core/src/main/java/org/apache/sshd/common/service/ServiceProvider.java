package org.apache.sshd.common.service;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 20/02/13
 * Time: 1:18 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ServiceProvider extends Service {

    /**
     * The session has sent the relevant ack packet (SSH_MSG_SERVICE_ACCEPT or SSH_MSG_USERAUTH_SUCCESS)
     * and the service can start sending to or expecting data from the remote end.
     */
    void start();
}
