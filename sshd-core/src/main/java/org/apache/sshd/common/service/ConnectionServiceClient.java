package org.apache.sshd.common.service;

import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 21/02/13
 * Time: 1:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionServiceClient extends ConnectionService<ClientSessionImpl> implements ServiceClient {

    public ConnectionServiceClient(ClientSessionImpl session, Object sessionLock,
                                   Map <String,GlobalRequest> globalRequestMap) {
        super(session, sessionLock, null, null, globalRequestMap);
    }

    public void serverAcceptedService() {
        startHeartBeat();
    }

    private void startHeartBeat() {
        String intervalStr = session.getFactoryManager().getProperties().get(ClientFactoryManager.HEARTBEAT_INTERVAL);
        try {
            int interval = intervalStr != null ? Integer.parseInt(intervalStr) : 0;
            if (interval > 0) {
                session.getFactoryManager().getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        sendHeartBeat();
                    }
                }, interval, interval, TimeUnit.MILLISECONDS);
            }
        } catch (NumberFormatException e) {
            logger.warn("Ignoring bad heartbeat interval: {}", intervalStr);
        }
    }

    private void sendHeartBeat() {
        try {
            Buffer buf = session.createBuffer(SshConstants.Message.SSH_MSG_GLOBAL_REQUEST, 0);
            String request = session.getFactoryManager().getProperties().get(ClientFactoryManager.HEARTBEAT_REQUEST);
            if (request == null) {
                request = "keepalive@sshd.apache.org";
            }
            buf.putString(request);
            buf.putBoolean(false);
            session.writePacket(buf);
        } catch (IOException e) {
            logger.info("Error sending keepalive message", e);
        }
    }
}
