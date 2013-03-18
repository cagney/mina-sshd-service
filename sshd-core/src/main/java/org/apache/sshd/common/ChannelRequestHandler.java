package org.apache.sshd.common;

import org.apache.sshd.common.util.Buffer;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 18/03/13
 * Time: 2:04 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ChannelRequestHandler {

    void process(Buffer buffer);
}
