package org.apache.sshd.common.service;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.forward.CancelTcpipForwardRequest;
import org.apache.sshd.common.forward.TcpipForwardRequest;
import org.apache.sshd.common.util.Buffer;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 08/03/13
 * Time: 2:43 PM
 * To change this template use File | Settings | File Templates.
 */
abstract public class GlobalRequest {

    private final String name;

    protected GlobalRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    abstract public void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer) throws Exception;

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

    public static class NoMoreSessions extends GlobalRequest {
        public NoMoreSessions() {
            super("no-more-sessions@openssh.com");
        }
        @Override
        public void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer) {
            connectionService.setAllowMoreSessions(false);
        }
    }

    /**
     * Helper function for creating the request map.
     * @param requests
     * @return
     */
    public static Map<String,GlobalRequest> create(GlobalRequest[] requests) {
        return create(new HashMap<String, GlobalRequest>(), requests);
    }
    /**
     * Helper function for creating the request map.
     * @param requests
     * @return
     */
    public static Map<String,GlobalRequest> create(Map<String,GlobalRequest> globalRequestMap, GlobalRequest[] requests) {
        for (GlobalRequest request : requests) {
            globalRequestMap.put(request.getName(), request);
        }
        return globalRequestMap;
    }

    /**
     * return the default map of supported requests.
     */
    public static Map<String,GlobalRequest> defaults() {
        return create(new GlobalRequest[] {
                new NoMoreSessions(),
                new TcpipForwardRequest(),
                new CancelTcpipForwardRequest(),
        });
    }
}
