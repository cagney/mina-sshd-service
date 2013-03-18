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

    void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer) throws Exception;

}
