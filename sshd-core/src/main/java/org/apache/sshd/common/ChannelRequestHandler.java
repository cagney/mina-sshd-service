package org.apache.sshd.common;

import org.apache.sshd.common.util.Buffer;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 18/03/13
 * Time: 2:04 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ChannelRequestHandler extends Name {

    /**
     * Process the request, return null if the method handled the reply, else return True or False.
     * @param channel
     * @param wantReply
     * @param buffer
     * @return
     */
    Boolean process(Channel channel, boolean wantReply, Buffer buffer);

}
