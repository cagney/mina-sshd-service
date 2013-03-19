package org.apache.sshd.common.service;

import org.apache.sshd.common.AbstractName;
import org.apache.sshd.common.GlobalRequestHandler;
import org.apache.sshd.common.util.Buffer;

/**
* Created with IntelliJ IDEA.
* User: cagney
* Date: 18/03/13
* Time: 12:47 PM
* To change this template use File | Settings | File Templates.
*/
public class NoMoreSessionsRequest extends AbstractName implements GlobalRequestHandler {
    public NoMoreSessionsRequest() {
        super("no-more-sessions@openssh.com");
    }

    public Boolean process(ConnectionService connectionService, boolean wantReply, Buffer buffer) {
        connectionService.setAllowMoreSessions(false);
        return Boolean.TRUE;
    }
}
