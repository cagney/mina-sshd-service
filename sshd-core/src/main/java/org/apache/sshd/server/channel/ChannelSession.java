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
package org.apache.sshd.server.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.common.*;
import org.apache.sshd.common.channel.ChannelOutputStream;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.service.ConnectionServiceProvider;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.common.util.IoUtils;
import org.apache.sshd.common.util.LoggingFilterOutputStream;
import org.apache.sshd.server.*;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.x11.X11ForwardSupport;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ChannelSession extends AbstractServerChannel {

    public static class Factory implements NamedFactory<Channel> {

        public String getName() {
            return "session";
        }

        public Channel create() {
            return new ChannelSession();
        }
    }

    protected static class StandardEnvironment implements Environment {

        private final Map<Signal, Set<SignalListener>> listeners;
        private final Map<String, String> env;
        private final Map<PtyMode, Integer> ptyModes;

        public StandardEnvironment() {
            listeners = new ConcurrentHashMap<Signal, Set<SignalListener>>(3);
            env = new ConcurrentHashMap<String, String>();
            ptyModes = new ConcurrentHashMap<PtyMode, Integer>();
        }

        public void addSignalListener(SignalListener listener, Signal... signals) {
            if (signals == null) {
                throw new IllegalArgumentException("signals may not be null");
            }
            if (listener == null) {
                throw new IllegalArgumentException("listener may not be null");
            }
            for (Signal s : signals) {
                getSignalListeners(s, true).add(listener);
            }
        }

        public void addSignalListener(SignalListener listener) {
            addSignalListener(listener, EnumSet.allOf(Signal.class));
        }

        public void addSignalListener(SignalListener listener, EnumSet<Signal> signals) {
            if (signals == null) {
                throw new IllegalArgumentException("signals may not be null");
            }
            addSignalListener(listener, signals.toArray(new Signal[signals.size()]));
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public Map<PtyMode, Integer> getPtyModes() {
            return ptyModes;
        }

        public void removeSignalListener(SignalListener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("listener may not be null");
            }
            for (Signal s : EnumSet.allOf(Signal.class)) {
                final Set<SignalListener> ls = getSignalListeners(s, false);
                if (ls != null) {
                    ls.remove(listener);
                }
            }
        }

        public void signal(Signal signal) {
            final Set<SignalListener> ls = getSignalListeners(signal, false);
            if (ls != null) {
                for (SignalListener l : ls) {
                    l.signal(signal);
                }
            }
        }

        /**
         * adds a variable to the environment. This method is called <code>set</code>
         * according to the name of the appropriate posix command <code>set</code>
         * @param key environment variable name
         * @param value environment variable value
         */
        public void set(String key, String value) {
            // TODO: listening for property changes would be nice too.
            getEnv().put(key, value);
        }

        protected Set<SignalListener> getSignalListeners(Signal signal, boolean create) {
            Set<SignalListener> ls = listeners.get(signal);
            if (ls == null && create) {
                synchronized (listeners) {
                    ls = listeners.get(signal);
                    if (ls == null) {
                        ls = new CopyOnWriteArraySet<SignalListener>();
                        listeners.put(signal, ls);
                    }
                }
            }
            // may be null in case create=false
            return ls;
        }

    }

    protected String type;
    protected OutputStream out;
    protected OutputStream err;
    protected Command command;
    protected ChannelDataReceiver receiver;
    protected StandardEnvironment env = new StandardEnvironment();

    public ChannelSession() {
        channelRequestProcessor.put(
                new EnvChannelRequestHandler(),
                new PtyChannelRequestHandler(),
                new WindowChangeChannelRequestHandler(),
                new SignalChannelRequestHandler(),
                new ShellChannelRequestHandler(),
                new ExecChannelRequestHandler(),
                new SubsystemChannelRequestHandler(),
                new AuthAgentChannelRequestHandler(),
                new X11ChannelRequestHandler(),
                new SimplePuTTYChannelRequestHandler());
    }

    public CloseFuture close(boolean immediately) {
        return super.close(immediately).addListener(new SshFutureListener() {
            public void operationComplete(SshFuture sshFuture) {
                if (command != null) {
                    command.destroy();
                    command = null;
                }
                remoteWindow.notifyClosed();
                IoUtils.closeQuietly(out, err, receiver);
            }
        });
    }

    @Override
    public void handleEof() throws IOException {
        super.handleEof();
        receiver.close();
    }

    protected void doWriteData(byte[] data, int off, int len) throws IOException {
        int r = receiver.data(this, data, off, len);
        if (r>0)
            localWindow.consumeAndCheck(r);
    }

    protected void doWriteExtendedData(byte[] data, int off, int len) throws IOException {
        throw new UnsupportedOperationException("Server channel does not support extended data");
    }

    protected boolean handleRequest(String type, Buffer buffer) throws IOException {
        if (type != null && type.endsWith("@putty.projects.tartarus.org")) {
            // Ignore but accept, more doc at
            // http://tartarus.org/~simon/putty-snapshots/htmldoc/AppendixF.html
            return true;
        }
        return false;
    }

    private class EnvChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        EnvChannelRequestHandler() {
            super("env");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) {
            String name = buffer.getString();
            String value = buffer.getString();
            addEnvVariable(name, value);
            log.debug("env for channel {}: {} = {}", new Object[] { id, name, value });
            return true;
        }
    }

    private class PtyChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        PtyChannelRequestHandler() {
            super("pty-req");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) {
            String term = buffer.getString();
            int tColumns = buffer.getInt();
            int tRows = buffer.getInt();
            int tWidth = buffer.getInt();
            int tHeight = buffer.getInt();
            byte[] modes = buffer.getBytes();
            for (int i = 0; i < modes.length && modes[i] != 0;) {
                PtyMode mode = PtyMode.fromInt(modes[i++]);
                int val  = ((modes[i++] << 24) & 0xff000000) |
                           ((modes[i++] << 16) & 0x00ff0000) |
                           ((modes[i++] <<  8) & 0x0000ff00) |
                           ((modes[i++]      ) & 0x000000ff);
                getEnvironment().getPtyModes().put(mode, val);
            }
            if (log.isDebugEnabled()) {
                log.debug("pty for channel {}: term={}, size=({} - {}), pixels=({}, {}), modes=[{}]", new Object[] { id, term, tColumns, tRows, tWidth, tHeight, getEnvironment().getPtyModes() });
            }
            addEnvVariable(Environment.ENV_TERM, term);
            addEnvVariable(Environment.ENV_COLUMNS, Integer.toString(tColumns));
            addEnvVariable(Environment.ENV_LINES, Integer.toString(tRows));
            return true;
        }
    }

    private class WindowChangeChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        WindowChangeChannelRequestHandler() {
            super("window-change");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) {
            int tColumns = buffer.getInt();
            int tRows = buffer.getInt();
            int tWidth = buffer.getInt();
            int tHeight = buffer.getInt();
            log.debug("window-change for channel {}: ({} - {}), ({}, {})", new Object[] { id, tColumns, tRows, tWidth, tHeight });

            final StandardEnvironment e = getEnvironment();
            e.set(Environment.ENV_COLUMNS, Integer.toString(tColumns));
            e.set(Environment.ENV_LINES, Integer.toString(tRows));
            e.signal(Signal.WINCH);
            return false;
        }
    }

    private class SignalChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        SignalChannelRequestHandler() {
            super("signal");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) {
            String name = buffer.getString();
            log.debug("Signal received on channel {}: {}", id, name);

            final Signal signal = Signal.get(name);
            if (signal != null) {
                getEnvironment().signal(signal);
            } else {
                log.warn("Unknown signal received: " + name);
            }

            return true;
        }
    }

    private class ShellChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        ShellChannelRequestHandler() {
            super("shell");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            if (type != null) {
                return false;
            }
            if (((ServerSession) session).getServerFactoryManager().getShellFactory() == null) {
                return false;
            }
            command = ((ServerSession) session).getServerFactoryManager().getShellFactory().create();
            prepareCommand();
            command.start(getEnvironment());
            type = getName();
            return true;
        }
    }

    private class ExecChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        ExecChannelRequestHandler() {
            super("exec");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            if (type != null) {
                return false;
            }
            String commandLine = buffer.getString();
            if (((ServerSession) session).getServerFactoryManager().getCommandFactory() == null) {
                return false;
            }
            if (log.isInfoEnabled()) {
                log.info("Executing command: {}", commandLine);
            }
            try {
                command = ((ServerSession) session).getServerFactoryManager().getCommandFactory().createCommand(commandLine);
            } catch (IllegalArgumentException iae) {
                // TODO: Shouldn't we log errors on the server side?
                return false;
            }
            prepareCommand();
            if (wantReply) {
                buffer = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_SUCCESS, 0);
                buffer.putInt(recipient);
                session.writePacket(buffer);
            }
            // Launch command
            command.start(getEnvironment());
            type = getName();
            return true;
        }
    }

    private class SubsystemChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        SubsystemChannelRequestHandler() {
            super("subsystem");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            if (type != null) {
                return false;
            }
            String subsystem = buffer.getString();
            List<NamedFactory<Command>> factories = ((ServerSession) session).getServerFactoryManager().getSubsystemFactories();
            if (factories == null) {
                return false;
            }
            command = NamedFactory.Utils.create(factories, subsystem);
            if (command == null) {
                return false;
            }
            prepareCommand();
            if (wantReply) {
                buffer = session.createBuffer(SshConstants.Message.SSH_MSG_CHANNEL_SUCCESS, 0);
                buffer.putInt(recipient);
                session.writePacket(buffer);
            }
            // Launch command
            command.start(getEnvironment());
            type = getName();
            return true;
        }
    }

    private class SimplePuTTYChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        SimplePuTTYChannelRequestHandler() {
            super("simple@putty.projects.tartarus.org");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            // http://tartarus.org/~simon/putty-snapshots/htmldoc/AppendixF.html
            return true;
        }
    }

    /**
     * For {@link Command} to install {@link ChannelDataReceiver}.
     * When you do this, {@link Command#setInputStream(InputStream)}
     * will no longer be invoked. If you call this method from {@link Command#start(Environment)},
     * the input stream you received in {@link Command#setInputStream(InputStream)} will
     * not read any data.
     */
    public void setDataReceiver(ChannelDataReceiver receiver) {
        this.receiver = receiver;
    }

    protected void prepareCommand() throws IOException {
        // Add the user
        addEnvVariable(Environment.ENV_USER, ((ServerSession) session).getUsername());
        // If the shell wants to be aware of the session, let's do that
        if (command instanceof SessionAware) {
            ((SessionAware) command).setSession((ServerSession) session);
        }
        if (command instanceof ChannelSessionAware) {
            ((ChannelSessionAware) command).setChannelSession(this);
        }
        // If the shell wants to be aware of the file system, let's do that too
        if (command instanceof FileSystemAware) {
            FileSystemFactory factory = ((ServerSession) session).getServerFactoryManager().getFileSystemFactory();
            ((FileSystemAware) command).setFileSystemView(factory.createFileSystemView(session));
        }
        out = new ChannelOutputStream(this, remoteWindow, log, SshConstants.Message.SSH_MSG_CHANNEL_DATA);
        err = new ChannelOutputStream(this, remoteWindow, log, SshConstants.Message.SSH_MSG_CHANNEL_EXTENDED_DATA);
        if (log != null && log.isTraceEnabled()) {
            // Wrap in logging filters
            out = new LoggingFilterOutputStream(out, "OUT:", log);
            err = new LoggingFilterOutputStream(err, "ERR:", log);
        }
        command.setOutputStream(out);
        command.setErrorStream(err);
        if (this.receiver==null) {
            // if the command hasn't installed any ChannelDataReceiver, install the default
            // and give the command an InputStream
            PipeDataReceiver recv = new PipeDataReceiver(localWindow);
            setDataReceiver(recv);
            command.setInputStream(recv.getIn());
        }
        command.setExitCallback(new ExitCallback() {
            public void onExit(int exitValue) {
                try {
                    closeShell(exitValue);
                } catch (IOException e) {
                    log.info("Error closing shell", e);
                }
            }
            public void onExit(int exitValue, String exitMessage) {
                onExit(exitValue);
            }
        });
    }

    protected int getPtyModeValue(PtyMode mode) {
        Integer v = getEnvironment().getPtyModes().get(mode);
        return v != null ? v : 0;
    }

    private class AuthAgentChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        AuthAgentChannelRequestHandler() {
            super("auth-agent-req@openssh.com");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            final ServerSession server = (ServerSession) session;
            final ForwardingFilter filter = server.getServerFactoryManager().getTcpipForwardingFilter();
            final SshAgentFactory factory = server.getServerFactoryManager().getAgentFactory();
            if (factory == null || (filter != null && !filter.canForwardAgent(server))) {
                return false;
            }

            String authSocket = connection.initAgentForward();
            addEnvVariable(SshAgent.SSH_AUTHSOCKET_ENV_NAME, authSocket);

            return true;
        }
    }

    private class X11ChannelRequestHandler extends AbstractName implements ChannelRequestHandler {
        X11ChannelRequestHandler() {
            super("x11-req");
        }
        public Boolean process(Channel channel, boolean wantReply, Buffer buffer) throws IOException {
            final ServerSession server = (ServerSession) session;
            final ForwardingFilter filter = server.getServerFactoryManager().getTcpipForwardingFilter();
            if (filter == null || !filter.canForwardX11(server)) {
                return false;
            }

            String display = connection.createX11Display(buffer.getBoolean(), buffer.getString(),
                                                         buffer.getString(), buffer.getInt());
            if (display == null) {
                return false;
            }

            addEnvVariable(X11ForwardSupport.ENV_DISPLAY, display);

            return true;
        }
    }

    protected void addEnvVariable(String name, String value) {
        getEnvironment().set(name, value);
    }

    protected StandardEnvironment getEnvironment() {
        return env;
    }

    protected void closeShell(int exitValue) throws IOException {
        if (!closing) {
            sendEof();
            sendExitStatus(exitValue);
            // TODO: We should wait for all streams to be consumed before closing the channel
            close(false);
        }
    }

}
