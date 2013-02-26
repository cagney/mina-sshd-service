package org.apache.sshd.common.service;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.util.Buffer;

/**
 * See RFC 4253 [SSH-TRANS] and the SSH_MSG_SERVICE_REQUEST packet.  Examples include ssh-userauth
 * and ssh-connection but developers are also free to implement their own custom service.
 */
public interface Service {

    /**
     * The name of the service as sent as part of the SSH_MSG_SERVICE_REQUEST message.
     * @return the service name
     */
    String getName();

    /**
     * Service the request.
     * @param buffer
     * @throws Exception
     */
    void process(SshConstants.Message cmd, Buffer buffer) throws Exception;

    /**
     * Close the service.
     * @param immediately
     *
     */
    void close(boolean immediately);
}
