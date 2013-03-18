package org.apache.sshd.common;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 18/03/13
 * Time: 2:06 PM
 * To change this template use File | Settings | File Templates.
 */
abstract public class AbstractChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
    protected AbstractChannelRequestHandler(String name) {
        super(name);
    }
}
