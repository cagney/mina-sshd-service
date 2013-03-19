package org.apache.sshd.common;

import org.apache.sshd.common.util.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 19/03/13
 * Time: 12:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class ChannelRequestProcessor {

    protected final Logger logger;

    private final NameMap<ChannelRequestHandler> globalRequestHandlerNameMap = new NameMap<ChannelRequestHandler>();

    public ChannelRequestProcessor(Logger logger) {
        this.logger = logger;
    }

    public ChannelRequestProcessor put(ChannelRequestHandler... globalRequestHandlers) {
        this.globalRequestHandlerNameMap.put(globalRequestHandlers);
        return this;
    }

    private void replySuccess(Channel channel, boolean wantReply) throws IOException {
        if (wantReply) {
            Session session = channel.getSession();
            Buffer buffer = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_SUCCESS, 0);
            buffer.putInt(channel.getRecipient());
            session.writePacket(buffer);
        }
    }

    private void replyFailure(Channel channel, boolean wantReply) throws IOException {
        if (wantReply) {
            Session session = channel.getSession();
            Buffer buffer = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_FAILURE, 0);
            buffer.putInt(channel.getRecipient());
            session.writePacket(buffer);
        }
    }

    public void process(Channel channel, Buffer buffer) throws IOException {
        String requestType = buffer.getString();
        boolean wantReply = buffer.getBoolean();
        logger.debug("Received SSH_MSG_CHANNEL_REQUEST {} on channel {} (wantReply {})", new Object[] { requestType, channel.getId(), wantReply });

        ChannelRequestHandler channelRequestHandler = globalRequestHandlerNameMap.get(requestType);
        if (channelRequestHandler == null) {
            logger.warn("Unknown channel request: {}", requestType);
            replyFailure(channel, wantReply);
        } else {
            Boolean result = channelRequestHandler.process(channel, wantReply, buffer);
            if (result != null) {
                if (result) {
                    replySuccess(channel, wantReply);
                } else {
                    replyFailure(channel, wantReply);
                }
            }
        }
    }

}
