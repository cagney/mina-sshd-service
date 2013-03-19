package org.apache.sshd.common;

import org.apache.sshd.common.Name;
import org.apache.sshd.common.service.ConnectionService;
import org.apache.sshd.common.util.Buffer;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 18/03/13
 * Time: 12:11 PM
 * To change this template use File | Settings | File Templates.
 */
public interface GlobalRequestHandler extends Name {

    /**
     * Process the ssh-connection global request.   Either the code can locally reply (and return null) or
     * return True (success) or False (failure) and the wrapping code generates the reply IFF wantReply.
     * @param connectionService
     * @param wantReply
     * @param buffer
     * @return True if the operation succeeded, False if the operation failed, and null if no reply is needed.
     * @throws Exception
     */
    Boolean process(ConnectionService connectionService, boolean wantReply, Buffer buffer) throws Exception;

}
