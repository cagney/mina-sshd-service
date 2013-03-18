package org.apache.sshd.common.service;

import org.apache.sshd.common.AbstractName;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.Buffer;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 08/03/13
 * Time: 2:43 PM
 * To change this template use File | Settings | File Templates.
 */
abstract public class AbstractGlobalRequestServer extends AbstractName implements GlobalRequestServer {

    protected AbstractGlobalRequestServer(String name) {
        super(name);
    }

    protected void replySuccess(ConnectionService connectionService, boolean reply) throws Exception {
        if (reply) {
            Session session = connectionService.getSession();
            Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_REQUEST_SUCCESS, 0);
            session.writePacket(response);
        }
    }

    protected void replyFailure(ConnectionService connectionService, boolean reply) throws Exception {
        if (reply) {
            Session session = connectionService.getSession();
            Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_REQUEST_FAILURE, 0);
            session.writePacket(response);
        }
    }

}
