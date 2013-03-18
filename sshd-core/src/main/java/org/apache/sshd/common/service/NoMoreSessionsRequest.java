package org.apache.sshd.common.service;

import org.apache.sshd.common.AbstractGlobalRequestHandler;
import org.apache.sshd.common.util.Buffer;

/**
* Created with IntelliJ IDEA.
* User: cagney
* Date: 18/03/13
* Time: 12:47 PM
* To change this template use File | Settings | File Templates.
*/
public class NoMoreSessionsRequest extends AbstractGlobalRequestHandler {
    public NoMoreSessionsRequest() {
        super("no-more-sessions@openssh.com");
    }

    public void process(ConnectionService connectionService, String request, boolean wantReply, Buffer buffer) {
        connectionService.setAllowMoreSessions(false);
    }
}
