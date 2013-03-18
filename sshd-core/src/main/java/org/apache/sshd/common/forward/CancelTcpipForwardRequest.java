package org.apache.sshd.common.forward;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.common.AbstractGlobalRequestHandler;
import org.apache.sshd.common.service.ConnectionService;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;

/**
* Created with IntelliJ IDEA.
* User: cagney
* Date: 08/03/13
* Time: 4:39 PM
* To change this template use File | Settings | File Templates.
*/
public class CancelTcpipForwardRequest extends AbstractGlobalRequestHandler {

    public static final String REQUEST = "cancel-tcpip-forward";

    public CancelTcpipForwardRequest() {
        super(REQUEST);
    }

    public void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer)  throws Exception{
        String address = buffer.getString();
        int port = buffer.getInt();
        connectionService.getTcpipForwarder().localPortForwardingCancelled(new SshdSocketAddress(address, port));
        replySuccess(connectionService, wantReply);
    }

    public static void request(ConnectionService connection, SshdSocketAddress remote) throws IOException {
        Buffer buffer = connection.getSession().createBuffer(SshConstants.Message.SSH_MSG_GLOBAL_REQUEST, 0);
        buffer.putString(REQUEST);
        buffer.putBoolean(false);
        buffer.putString(remote.getHostName());
        buffer.putInt(remote.getPort());
        connection.getSession().writePacket(buffer);
    }
}
