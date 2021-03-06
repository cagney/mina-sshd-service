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
package org.apache.sshd.common.forward;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.*;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.service.ConnectionService;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class DefaultTcpipForwarder extends IoHandlerAdapter implements TcpipForwarder {

    private final ConnectionService connection;
    private final Map<Integer, SshdSocketAddress> localToRemote = new HashMap<Integer, SshdSocketAddress>();
    private final Map<Integer, SshdSocketAddress> remoteToLocal = new HashMap<Integer, SshdSocketAddress>();
    private final Set<SshdSocketAddress> localForwards = new HashSet<SshdSocketAddress>();
    protected IoAcceptor acceptor;

    public DefaultTcpipForwarder(ConnectionService connection) {
        this.connection = connection;
    }

    //
    // TcpIpForwarder implementation
    //

    public synchronized SshdSocketAddress startLocalPortForwarding(SshdSocketAddress local, SshdSocketAddress remote) throws Exception {
        if (local == null) {
            throw new IllegalArgumentException("Local address is null");
        }
        if (remote == null) {
            throw new IllegalArgumentException("Remote address is null");
        }
        if (local.getPort() < 0) {
            throw new IllegalArgumentException("Invalid local port: " + local.getPort());
        }
        SshdSocketAddress bound = doBind(local);
        localToRemote.put(bound.getPort(), remote);
        return bound;
    }

    public synchronized void stopLocalPortForwarding(SshdSocketAddress local) throws Exception {
        if (localToRemote.remove(local.getPort()) != null && acceptor != null) {
            acceptor.unbind(local.toInetSocketAddress());
            if (acceptor.getLocalAddresses().isEmpty()) {
                close();
            }
        }
    }

    public synchronized SshdSocketAddress startRemotePortForwarding(SshdSocketAddress remote, SshdSocketAddress local) throws Exception {
        Buffer result = TcpipForwardRequest.request(connection, remote, local);
        if (result == null) {
            throw new SshException("Tcpip forwarding request denied by server");
        }
        int port = result.getInt();
        // TODO: Is it really safe to only store the local address after the request ?
        remoteToLocal.put(port, local);
        return new SshdSocketAddress(remote.getHostName(), port);
    }

    public synchronized void stopRemotePortForwarding(SshdSocketAddress remote) throws Exception {
        if (remoteToLocal.remove(remote.getPort()) != null) {
            CancelTcpipForwardRequest.request(connection, remote);
        }
    }

    public synchronized SshdSocketAddress getForwardedPort(int remotePort) {
        return remoteToLocal.get(remotePort);
    }

    public synchronized SshdSocketAddress localPortForwardingRequested(SshdSocketAddress local) throws Exception {
        if (local == null) {
            throw new IllegalArgumentException("Local address is null");
        }
        if (local.getPort() < 0) {
            throw new IllegalArgumentException("Invalid local port: " + local.getPort());
        }
        final ForwardingFilter filter = connection.getSession().getFactoryManager().getTcpipForwardingFilter();
        if (filter == null || !filter.canListen(local, connection.getSession())) {
            throw new IOException("Rejected address: " + local);
        }
        SshdSocketAddress bound = doBind(local);
        localForwards.add(bound);
        return bound;
    }

    public synchronized void localPortForwardingCancelled(SshdSocketAddress local) throws Exception {
        if (localForwards.remove(local) && acceptor != null) {
            acceptor.unbind(local.toInetSocketAddress());
            if (acceptor.getLocalAddresses().isEmpty()) {
                close();
            }
        }
    }

    public synchronized void initialize() {
        if (this.acceptor == null) {
            NioSocketAcceptor acceptor = connection.getSession().getFactoryManager().getTcpipForwardingAcceptorFactory().createNioSocketAcceptor();
            acceptor.setHandler(this);
            acceptor.setReuseAddress(true);
            acceptor.getFilterChain().addLast("executor", new ExecutorFilter(EnumSet.complementOf(EnumSet.of(IoEventType.SESSION_CREATED)).toArray(new IoEventType[0])));
            this.acceptor = acceptor;
        }
    }

    public synchronized void close() {
        if (acceptor != null) {
            acceptor.dispose();
            acceptor = null;
        }
    }

    //
    // IoHandler implementation
    //

    @Override
    public void sessionCreated(final IoSession session) throws Exception {
        final TcpipClientChannel channel;
        int localPort = ((InetSocketAddress) session.getLocalAddress()).getPort();
        if (localToRemote.containsKey(localPort)) {
            SshdSocketAddress remote = localToRemote.get(localPort);
            channel = new TcpipClientChannel(TcpipClientChannel.Type.Direct, session, remote);
        } else {
            channel = new TcpipClientChannel(TcpipClientChannel.Type.Forwarded, session, null);
        }
        session.setAttribute(TcpipClientChannel.class, channel);
        this.connection.registerChannel(channel);
        channel.open().addListener(new SshFutureListener<OpenFuture>() {
            public void operationComplete(OpenFuture future) {
                Throwable t = future.getException();
                if (t != null) {
                    DefaultTcpipForwarder.this.connection.unregisterChannel(channel);
                    channel.close(false);
                }
            }
        });
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        TcpipClientChannel channel = (TcpipClientChannel) session.getAttribute(TcpipClientChannel.class);
        if (channel != null) {
            channel.close(false);
        }
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        TcpipClientChannel channel = (TcpipClientChannel) session.getAttribute(TcpipClientChannel.class);
        IoBuffer ioBuffer = (IoBuffer) message;
        int r = ioBuffer.remaining();
        byte[] b = new byte[r];
        ioBuffer.get(b, 0, r);
        channel.waitFor(ClientChannel.OPENED | ClientChannel.CLOSED, Long.MAX_VALUE);
        channel.getOut().write(b, 0, r);
        channel.getOut().flush();
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        cause.printStackTrace();
        session.close(false);
    }

    //
    // Private methods
    //

    private SshdSocketAddress doBind(SshdSocketAddress address) throws IOException {
        initialize();
        Set<SocketAddress> before = acceptor.getLocalAddresses();
        try {
            acceptor.bind(address.toInetSocketAddress());
            Set<SocketAddress> after = acceptor.getLocalAddresses();
            after.removeAll(before);
            if (after.isEmpty()) {
                throw new IOException("Error binding to " + address + ": no local addresses bound");
            }
            if (after.size() > 1) {
                throw new IOException("Multiple local addresses have been bound for " + address);
            }
            InetSocketAddress result = (InetSocketAddress) after.iterator().next();
            return new SshdSocketAddress(address.getHostName(), result.getPort());
        } catch (IOException bindErr) {
            if (acceptor.getLocalAddresses().isEmpty()) {
                close();
            }
            throw bindErr;
        }
    }

}
