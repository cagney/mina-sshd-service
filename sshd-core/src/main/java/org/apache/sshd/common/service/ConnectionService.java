package org.apache.sshd.common.service;

import org.apache.sshd.agent.common.AgentForwardSupport;
import org.apache.sshd.client.channel.AbstractClientChannel;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.TcpipForwarder;
import org.apache.sshd.common.future.SshFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.channel.OpenChannelException;
import org.apache.sshd.server.x11.X11ForwardSupport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 19/02/13
 * Time: 2:58 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ConnectionService<T extends AbstractSession> extends AbstractService<T> {

    public static final String SERVICE_NAME = "ssh-connection";

    private boolean allowMoreSessions = true;
    /** Map of channels keyed by the identifier */
    protected final Map<Integer, Channel> channels = new ConcurrentHashMap<Integer, Channel>();
    /** The tcpip forwarder */
    protected TcpipForwarder tcpipForwarder;
    /** Next channel identifier */
    protected int nextChannelId;
    protected boolean closing;
    protected final Object requestLock = new Object();
    protected final AtomicReference<Buffer> requestResult = new AtomicReference<Buffer>();

    private AgentForwardSupport agentForward;
    private X11ForwardSupport x11Forward;
    private final Map<String,GlobalRequest> globalRequestMap;

    protected ConnectionService(String serviceName, T session, Object sessionLock, AgentForwardSupport agentForward,
                                X11ForwardSupport x11Forward, Map<String,GlobalRequest> globalRequestMap) {
        super(serviceName, session, sessionLock);
        this.agentForward = agentForward;
        this.x11Forward = x11Forward;
        this.tcpipForwarder = session.getFactoryManager().getTcpipForwarderFactory().create(this);
        this.globalRequestMap = globalRequestMap;
    }

    public void close(final boolean immediately) {
        if (agentForward != null) {
            agentForward.close();
        }
        if (x11Forward != null) {
            x11Forward.close();
        }
        if (tcpipForwarder != null) {
            tcpipForwarder.close();
        }
        if (!closing) {
            try {
                closing = true;
                logger.debug("Closing session");
                Channel[] channelToClose = channels.values().toArray(new Channel[channels.values().size()]);
                if (channelToClose.length > 0) {
                    final AtomicInteger latch = new AtomicInteger(channelToClose.length);
                    for (Channel channel : channelToClose) {
                        logger.debug("Closing channel {}", channel.getId());
                        channel.close(immediately).addListener(new SshFutureListener() {
                            public void operationComplete(SshFuture sshFuture) {
                                if (latch.decrementAndGet() == 0) {
                                    session.closeIoSession(true);
                                }
                            }
                        });
                    }
                } else {
                    logger.debug("Closing IoSession");
                    session.closeIoSession(immediately);
                }
            } catch (Throwable t) {
                logger.warn("Error closing session", t);
            }
        }
    }

    public void process(SshConstants.Message cmd, Buffer buffer) throws Exception {
        switch (cmd) {
            case SSH_MSG_REQUEST_SUCCESS:
                requestSuccess(buffer);
                break;
            case SSH_MSG_REQUEST_FAILURE:
                requestFailure(buffer);
                break;
            case SSH_MSG_CHANNEL_OPEN:
                channelOpen(buffer);
                break;
            case SSH_MSG_CHANNEL_OPEN_CONFIRMATION:
                channelOpenConfirmation(buffer);
                break;
            case SSH_MSG_CHANNEL_OPEN_FAILURE:
                channelOpenFailure(buffer);
                break;
            case SSH_MSG_CHANNEL_REQUEST:
                channelRequest(buffer);
                break;
            case SSH_MSG_CHANNEL_DATA:
                channelData(buffer);
                break;
            case SSH_MSG_CHANNEL_EXTENDED_DATA:
                channelExtendedData(buffer);
                break;
            case SSH_MSG_CHANNEL_WINDOW_ADJUST:
                channelWindowAdjust(buffer);
                break;
            case SSH_MSG_CHANNEL_EOF:
                channelEof(buffer);
                break;
            case SSH_MSG_CHANNEL_CLOSE:
                channelClose(buffer);
                break;
            case SSH_MSG_GLOBAL_REQUEST:
                processGlobalRequest(buffer);
                break;
        }
    }

    private void channelOpen(Buffer buffer) throws Exception {
        String type = buffer.getString();
        final int id = buffer.getInt();
        final int rwsize = buffer.getInt();
        final int rmpsize = buffer.getInt();

        logger.debug("Received SSH_MSG_CHANNEL_OPEN {}", type);

        if (closing) {
            Buffer buf = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN_FAILURE, 0);
            buf.putInt(id);
            buf.putInt(SshConstants.SSH_OPEN_CONNECT_FAILED);
            buf.putString("SSH server is shutting down: " + type);
            buf.putString("");
            session.writePacket(buf);
            return;
        }
        if (!allowMoreSessions) {
            Buffer buf = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN_FAILURE, 0);
            buf.putInt(id);
            buf.putInt(SshConstants.SSH_OPEN_CONNECT_FAILED);
            buf.putString("additional sessions disabled");
            buf.putString("");
            session.writePacket(buf);
            return;
        }

        final Channel channel = NamedFactory.Utils.create(session.getFactoryManager().getChannelFactories(), type);
        if (channel == null) {
            Buffer buf = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN_FAILURE, 0);
            buf.putInt(id);
            buf.putInt(SshConstants.SSH_OPEN_UNKNOWN_CHANNEL_TYPE);
            buf.putString("Unsupported channel type: " + type);
            buf.putString("");
            session.writePacket(buf);
            return;
        }

        final int channelId = getNextChannelId();
        channels.put(channelId, channel);
        channel.init(session, this, channelId);
        channel.open(id, rwsize, rmpsize, buffer).addListener(new SshFutureListener<OpenFuture>() {
            public void operationComplete(OpenFuture future) {
                try {
                    if (future.isOpened()) {
                        Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN_CONFIRMATION, 0);
                        response.putInt(id);
                        response.putInt(channelId);
                        response.putInt(channel.getLocalWindow().getSize());
                        response.putInt(channel.getLocalWindow().getPacketSize());
                        session.writePacket(response);
                    } else if (future.getException() != null) {
                        Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_OPEN_FAILURE, 0);
                        response.putInt(id);
                        if (future.getException() instanceof OpenChannelException) {
                            response.putInt(((OpenChannelException) future.getException()).getReasonCode());
                            response.putString(future.getException().getMessage());
                        } else {
                            response.putInt(0);
                            response.putString("Error opening channel: " + future.getException().getMessage());
                        }
                        response.putString("");
                        session.writePacket(response);
                    }
                } catch (IOException e) {
                    session.exceptionCaught(e);
                }
            }
        });
    }

    private void processGlobalRequest(Buffer buffer) throws Exception {
        String request = buffer.getString();
        boolean wantReply = buffer.getBoolean();
        logger.debug("Received SSH_MSG_GLOBAL_REQUEST {}", request);
        if (request.startsWith("keepalive@")) {
            // want error response
        } else {
            GlobalRequest globalRequest = globalRequestMap.get(request);
            if (globalRequest != null) {
                globalRequest.process(this, request, wantReply, buffer);
                return;
            }
            logger.warn("Unknown global request: {}", request);
        }
        if (wantReply) {
            Buffer response = session.createBuffer(SshConstants.Message.SSH_MSG_REQUEST_FAILURE, 0);
            session.writePacket(response);
        }
    }

    public int getNextChannelId() {
        synchronized (channels) {
            return nextChannelId++;
        }
    }

    public int registerChannel(Channel channel) throws Exception {
        int channelId = getNextChannelId();
        channel.init(session, this, channelId);
        channels.put(channelId, channel);
        return channelId;
    }

    public void channelOpenConfirmation(Buffer buffer) throws IOException {
        Channel channel = getChannel(buffer);
        logger.debug("Received SSH_MSG_CHANNEL_OPEN_CONFIRMATION on channel {}", channel.getId());
        int recipient = buffer.getInt();
        int rwsize = buffer.getInt();
        int rmpsize = buffer.getInt();
        channel.handleOpenSuccess(recipient, rwsize, rmpsize, buffer);
    }

    public void channelOpenFailure(Buffer buffer) throws IOException {
        AbstractClientChannel channel = (AbstractClientChannel) getChannel(buffer);
        logger.debug("Received SSH_MSG_CHANNEL_OPEN_FAILURE on channel {}", channel.getId());
        channels.remove(channel.getId());
        channel.handleOpenFailure(buffer);
    }

    /**
     * Process incoming data on a channel
     *
     * @param buffer the buffer containing the data
     * @throws Exception if an error occurs
     */
    public void channelData(Buffer buffer) throws Exception {
        Channel channel = getChannel(buffer);
        channel.handleData(buffer);
    }

    /**
     * Process incoming extended data on a channel
     *
     * @param buffer the buffer containing the data
     * @throws Exception if an error occurs
     */
    public void channelExtendedData(Buffer buffer) throws Exception {
        Channel channel = getChannel(buffer);
        channel.handleExtendedData(buffer);
    }

    /**
     * Process a window adjust packet on a channel
     *
     * @param buffer the buffer containing the window adjustement parameters
     * @throws Exception if an error occurs
     */
    public void channelWindowAdjust(Buffer buffer) throws Exception {
        try {
            Channel channel = getChannel(buffer);
            channel.handleWindowAdjust(buffer);
        } catch (SshException e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * Process end of file on a channel
     *
     * @param buffer the buffer containing the packet
     * @throws Exception if an error occurs
     */
    public void channelEof(Buffer buffer) throws Exception {
        Channel channel = getChannel(buffer);
        channel.handleEof();
    }

    /**
     * Close a channel due to a close packet received
     *
     * @param buffer the buffer containing the packet
     * @throws Exception if an error occurs
     */
    public void channelClose(Buffer buffer) throws Exception {
        Channel channel = getChannel(buffer);
        channel.handleClose();
        unregisterChannel(channel);
    }

    /**
     * Remove this channel from the list of managed channels
     *
     * @param channel the channel
     */
    public void unregisterChannel(Channel channel) {
        channels.remove(channel.getId());
    }

    /**
     * Service a request on a channel
     *
     * @param buffer the buffer containing the request
     * @throws Exception if an error occurs
     */
    public void channelRequest(Buffer buffer) throws IOException {
        Channel channel = getChannel(buffer);
        channel.handleRequest(buffer);
    }

    /**
     * Process a failure on a channel
     *
     * @param buffer the buffer containing the packet
     * @throws Exception if an error occurs
     */
    public void channelFailure(Buffer buffer) throws Exception {
        Channel channel = getChannel(buffer);
        channel.handleFailure();
    }

    /**
     * Retrieve the channel designated by the given packet
     *
     * @param buffer the incoming packet
     * @return the target channel
     * @throws IOException if the channel does not exists
     */
    protected Channel getChannel(Buffer buffer) throws IOException {
        int recipient = buffer.getInt();
        Channel channel = channels.get(recipient);
        if (channel == null) {
            buffer.rpos(buffer.rpos() - 5);
            SshConstants.Message cmd = buffer.getCommand();
            throw new SshException("Received " + cmd + " on unknown channel " + recipient);
        }
        return channel;
    }

    public String initAgentForward() throws IOException {
        return agentForward.initialize();
    }

    public String createX11Display(boolean singleConnection, String authenticationProtocol, String authenticationCookie, int screen) throws IOException {
        return x11Forward.createDisplay(singleConnection, authenticationProtocol, authenticationCookie, screen);
    }

    public TcpipForwarder getTcpipForwarder() {
        return tcpipForwarder;
    }

    /**
     * Send a global request and wait for the response.
     * This must only be used when sending a SSH_MSG_GLOBAL_REQUEST with a result expected,
     * else it will wait forever.
     *
     * @param buffer the buffer containing the global request
     * @return <code>true</code> if the request was successful, <code>false</code> otherwise.
     * @throws java.io.IOException if an error occured when encoding sending the packet
     */
    public Buffer request(Buffer buffer) throws IOException {
        synchronized (requestLock) {
            try {
                synchronized (requestResult) {
                    session.writePacket(buffer);
                    requestResult.wait();
                    return requestResult.get();
                }
            } catch (InterruptedException e) {
                throw (InterruptedIOException) new InterruptedIOException().initCause(e);
            }
        }
    }

    private void requestSuccess(Buffer buffer) throws Exception{
        synchronized (requestResult) {
            requestResult.set(new Buffer(buffer.getCompactData()));
            requestResult.notify();
        }
    }

    private void requestFailure(Buffer buffer) throws Exception{
        synchronized (requestResult) {
            requestResult.set(null);
            requestResult.notify();
        }
    }

    public void setAllowMoreSessions(boolean allowMoreSessions) {
        this.allowMoreSessions = allowMoreSessions;
    }
}
