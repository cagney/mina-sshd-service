/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.client.session;

import java.io.IOException;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.ServerKeyVerifier;
import org.apache.sshd.client.auth.UserAuthAgent;
import org.apache.sshd.client.auth.UserAuthPassword;
import org.apache.sshd.client.auth.UserAuthPublicKey;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.service.ConnectionService;
import org.apache.sshd.common.service.ServiceClient;
import org.apache.sshd.common.service.UserAuthServiceClient;
import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.common.util.Buffer;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ClientSessionImpl extends AbstractSession<ServiceClient> implements ClientSession {

    /**
     * For clients to store their own metadata
     */
    private Map<Object, Object> metadataMap = new HashMap<Object, Object>();
    private final LinkedList<ServiceClient> pendingServices;

    public ClientSessionImpl(ClientFactoryManager client, IoSession session) throws Exception {
        super(client, session);
        log.info("Session created...");
        // Need to set the initial service early as calling code likes to start trying to
        // manipulate it before the connection has even been established.  For instance, to
        // set the authPassword.
        this.pendingServices = client.getServiceClientsFactory().create(this, lock);
        // install the initial service
        this.currentService = this.pendingServices.pollFirst();
        sendClientIdentification();
        sendKexInit();
    }

    public ClientFactoryManager getClientFactoryManager() {
        return (ClientFactoryManager) factoryManager;
    }

    public KeyExchange getKex() {
        return kex;
    }

    public AuthFuture authAgent(String username) throws IOException {
        synchronized (lock) {
            return findService(UserAuthServiceClient.class).auth(new UserAuthAgent(this, this.pendingServices.peekFirst(), username));
        }
    }

    public AuthFuture authPassword(String username, String password) throws IOException {
        synchronized (lock) {
            return findService(UserAuthServiceClient.class).auth(new UserAuthPassword(this, this.pendingServices.peekFirst(), username, password));
        }
    }

    public AuthFuture authPublicKey(String username, KeyPair key) throws IOException {
        synchronized (lock) {
            return findService(UserAuthServiceClient.class).auth(new UserAuthPublicKey(this, this.pendingServices.peekFirst(), username, key));
        }
    }

    public ClientChannel createChannel(String type) throws Exception {
        return createChannel(type, null);
    }

    public ClientChannel createChannel(String type, String subType) throws Exception {
        if (ClientChannel.CHANNEL_SHELL.equals(type)) {
            return createShellChannel();
        } else if (ClientChannel.CHANNEL_EXEC.equals(type)) {
            return createExecChannel(subType);
        } else if (ClientChannel.CHANNEL_SUBSYSTEM.equals(type)) {
            return createSubsystemChannel(subType);
        } else {
            throw new IllegalArgumentException("Unsupported channel type " + type);
        }
    }

    public ChannelShell createShellChannel() throws Exception {
        ChannelShell channel = new ChannelShell();
        registerChannel(channel);
        return channel;
    }

    public ChannelExec createExecChannel(String command) throws Exception {
        ChannelExec channel = new ChannelExec(command);
        registerChannel(channel);
        return channel;
    }

    public ChannelSubsystem createSubsystemChannel(String subsystem) throws Exception {
        ChannelSubsystem channel = new ChannelSubsystem(subsystem);
        registerChannel(channel);
        return channel;
    }

    public ChannelDirectTcpip createDirectTcpipChannel(SshdSocketAddress local, SshdSocketAddress remote) throws Exception {
        ChannelDirectTcpip channel = new ChannelDirectTcpip(local, remote);
        registerChannel(channel);
        return channel;
    }

    public SshdSocketAddress startLocalPortForwarding(SshdSocketAddress local, SshdSocketAddress remote) throws Exception {
        return findService(ConnectionService.class).getTcpipForwarder().startLocalPortForwarding(local, remote);
    }

    public void stopLocalPortForwarding(SshdSocketAddress local) throws Exception {
        findService(ConnectionService.class).getTcpipForwarder().stopLocalPortForwarding(local);
    }

    public SshdSocketAddress startRemotePortForwarding(SshdSocketAddress remote, SshdSocketAddress local) throws Exception {
        return findService(ConnectionService.class).getTcpipForwarder().startRemotePortForwarding(remote, local);
    }

    public void stopRemotePortForwarding(SshdSocketAddress remote) throws Exception {
        findService(ConnectionService.class).getTcpipForwarder().stopRemotePortForwarding(remote);
    }

    protected void handleMessage(Buffer buffer) throws Exception {
        synchronized (lock) {
            doHandleMessage(buffer);
        }
    }

    protected void doHandleMessage(Buffer buffer) throws Exception {
        SshConstants.Message cmd = buffer.getCommand();
        log.debug("Received packet {}", cmd);
        switch (cmd) {
            case SSH_MSG_DISCONNECT: {
                int code = buffer.getInt();
                String msg = buffer.getString();
                log.info("Received SSH_MSG_DISCONNECT (reason={}, msg={})", code, msg);
                close(false);
                break;
            }
            case SSH_MSG_UNIMPLEMENTED: {
                int code = buffer.getInt();
                log.info("Received SSH_MSG_UNIMPLEMENTED #{}", code);
                break;
            }
            case SSH_MSG_DEBUG: {
                boolean display = buffer.getBoolean();
                String msg = buffer.getString();
                log.info("Received SSH_MSG_DEBUG (display={}) '{}'", display, msg);
                break;
            }
            case SSH_MSG_IGNORE:
                log.info("Received SSH_MSG_IGNORE");
                break;
            default:
                switch (getState()) {
                    case ReceiveKexInit:
                        if (cmd != SshConstants.Message.SSH_MSG_KEXINIT) {
                            log.error("Ignoring command " + cmd + " while waiting for " + SshConstants.Message.SSH_MSG_KEXINIT);
                            break;
                        }
                        log.info("Received SSH_MSG_KEXINIT");
                        receiveKexInit(buffer);
                        negociate();
                        kex = NamedFactory.Utils.create(factoryManager.getKeyExchangeFactories(), negociated[SshConstants.PROPOSAL_KEX_ALGS]);
                        kex.init(this, serverVersion.getBytes(), clientVersion.getBytes(), I_S, I_C);
                        setState(State.Kex);
                        break;
                    case Kex:
                        buffer.rpos(buffer.rpos() - 1);
                        if (kex.next(buffer)) {
                            checkHost();
                            sendNewKeys();
                            setState(State.ReceiveNewKeys);
                        }
                        break;
                    case ReceiveNewKeys:
                        if (cmd != SshConstants.Message.SSH_MSG_NEWKEYS) {
                            disconnect(SshConstants.SSH2_DISCONNECT_PROTOCOL_ERROR, "Protocol error: expected packet SSH_MSG_NEWKEYS, got " + cmd);
                            return;
                        }
                        log.info("Received SSH_MSG_NEWKEYS");
                        receiveNewKeys(false);
                        log.info("Send SSH_MSG_SERVICE_REQUEST for {}", this.currentService.getName());
                        Buffer request = createBuffer(SshConstants.Message.SSH_MSG_SERVICE_REQUEST, 0);
                        request.putString(this.currentService.getName());
                        writePacket(request);
                        setState(State.ServiceRequestSent);
                        // Assuming that MINA-SSHD only implements "explicit server authentication" it is permissible
                        // for the client's service to start sending data before the service-accept has been received.
                        // If "implicit authentication" were to ever be supported, then this would need to be
                        // called after service-accept comes back.  See SSH-TRANSPORT.
                        this.currentService.start();
                        break;
                    case ServiceRequestSent:
                        if (cmd != SshConstants.Message.SSH_MSG_SERVICE_ACCEPT) {
                            disconnect(SshConstants.SSH2_DISCONNECT_PROTOCOL_ERROR, "Protocol error: expected packet SSH_MSG_SERVICE_ACCEPT, got " + cmd);
                            return;
                        }
                        setState(State.Running);
                        break;
                    case Running:
                        currentService.process(cmd, buffer);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported state: " + getState());
                }
        }
    }

    public void setState(State newState) {
        synchronized (lock) {
            super.setState(newState);
            lock.notifyAll();
        }
    }

    public void switchToService(boolean authenticated, String username, ServiceClient service) {
        this.authed = authenticated;
        this.username = username;
        this.currentService = service;
        service.start();
    }

    protected boolean readIdentification(Buffer buffer) throws IOException {
        serverVersion = doReadIdentification(buffer);
        if (serverVersion == null) {
            return false;
        }
        log.info("Server version string: {}", serverVersion);
        if (!(serverVersion.startsWith("SSH-2.0-") || serverVersion.startsWith("SSH-1.99-"))) {
            throw new SshException(SshConstants.SSH2_DISCONNECT_PROTOCOL_VERSION_NOT_SUPPORTED,
                    "Unsupported protocol version: " + serverVersion);
        }
        return true;
    }

    private void sendClientIdentification() {
        clientVersion = "SSH-2.0-" + getFactoryManager().getVersion();
        sendIdentification(clientVersion);
    }

    private void sendKexInit() throws Exception {
        clientProposal = createProposal(KeyPairProvider.SSH_RSA + "," + KeyPairProvider.SSH_DSS);
        I_C = sendKexInit(clientProposal);
    }

    private void receiveKexInit(Buffer buffer) throws Exception {
        serverProposal = new String[SshConstants.PROPOSAL_MAX];
        I_S = receiveKexInit(buffer, serverProposal);
    }

    private void checkHost() throws SshException {
        ServerKeyVerifier serverKeyVerifier = getClientFactoryManager().getServerKeyVerifier();
        SocketAddress remoteAddress = ioSession.getRemoteAddress();

        if (!serverKeyVerifier.verifyServerKey(this, remoteAddress, kex.getServerKey())) {
            throw new SshException("Server key did not validate");
        }
    }

    public Map<Object, Object> getMetadataMap() {
        return metadataMap;
    }

    protected <T> T findService(Class<T> target) {
        if (currentService != null && target.isInstance(currentService)) {
            return (T)currentService;
        } else {
            for (ServiceClient service : pendingServices) {
                if (target.isInstance(service)) {
                    return (T)service;
                }
            }
            log.warn("Attempted to access unknown service {}", target.getSimpleName());
            return null;
        }
    }
}
