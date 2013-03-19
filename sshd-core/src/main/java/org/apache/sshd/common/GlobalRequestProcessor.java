package org.apache.sshd.common;

import org.apache.sshd.common.service.ConnectionService;
import org.apache.sshd.common.util.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 19/03/13
 * Time: 11:27 AM
 * To change this template use File | Settings | File Templates.
 */
public class GlobalRequestProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NameMap<GlobalRequestHandler> globalRequestHandlerNameMap;

    public GlobalRequestProcessor(GlobalRequestHandler... globalRequestHandlers) {
        this.globalRequestHandlerNameMap = new NameMap<GlobalRequestHandler>(globalRequestHandlers);
    }

    private void replySuccess(ConnectionService connectionService, boolean wantReply) throws Exception {
        if (wantReply) {
            Session session = connectionService.getSession();
            Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_REQUEST_SUCCESS, 0);
            session.writePacket(response);
        }
    }

    private void replyFailure(ConnectionService connectionService, boolean wantReply) throws Exception {
        if (wantReply) {
            Session session = connectionService.getSession();
            Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_REQUEST_FAILURE, 0);
            session.writePacket(response);
        }
    }

    public void process(ConnectionService connectionService, Buffer buffer) throws Exception {
        String requestName = buffer.getString();
        boolean wantReply = buffer.getBoolean();
        logger.debug("Received SSH_MSG_GLOBAL_REQUEST {} (wantReply {})", requestName, wantReply);
        if (requestName.startsWith("keepalive@")) {
            // want error response; TODO: get rid of the hack.
            replyFailure(connectionService, wantReply);
        } else {
            GlobalRequestHandler globalRequestHandler = globalRequestHandlerNameMap.get(requestName);
            if (globalRequestHandler == null) {
                logger.warn("Unknown global request: {}", requestName);
                replyFailure(connectionService, wantReply);
            } else {
                Boolean result = globalRequestHandler.process(connectionService, wantReply, buffer);
                if (result != null) {
                    if (result) {
                        replySuccess(connectionService, wantReply);
                    } else {
                        replyFailure(connectionService, wantReply);
                    }
                }
            }
        }
    }

}
