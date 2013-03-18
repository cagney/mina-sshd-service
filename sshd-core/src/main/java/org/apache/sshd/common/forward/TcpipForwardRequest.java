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
* Time: 4:38 PM
* To change this template use File | Settings | File Templates.
*/
public class TcpipForwardRequest extends AbstractGlobalRequestHandler {

    public static final String REQUEST = "tcpip-forward";

    public TcpipForwardRequest() {
        super(REQUEST);
    }

    public void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer) throws Exception {
        String address = buffer.getString();
        int port = buffer.getInt();
        try {
            SshdSocketAddress bound = connectionService.getTcpipForwarder().localPortForwardingRequested(new SshdSocketAddress(address, port));
            port = bound.getPort();
            if (wantReply) {
                Buffer response = connectionService.getSession().createBuffer(SshConstants.Message.SSH_MSG_REQUEST_SUCCESS, 0);
                response.putInt(port);
                connectionService.getSession().writePacket(response);
            }
        } catch (Exception e) {
            replyFailure(connectionService, wantReply);
        }
    }

    public static Buffer request(ConnectionService connection, SshdSocketAddress remote, SshdSocketAddress local) throws IOException {
        Buffer buffer = connection.getSession().createBuffer(SshConstants.Message.SSH_MSG_GLOBAL_REQUEST, 0);
        buffer.putString(REQUEST);
        buffer.putBoolean(true);
        buffer.putString(remote.getHostName());
        buffer.putInt(remote.getPort());
        return connection.request(buffer);
    }
}
