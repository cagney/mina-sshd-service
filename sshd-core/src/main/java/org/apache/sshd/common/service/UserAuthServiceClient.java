package org.apache.sshd.common.service;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.UserAuth;
import org.apache.sshd.client.auth.UserAuthAgent;
import org.apache.sshd.client.auth.UserAuthPassword;
import org.apache.sshd.client.auth.UserAuthPublicKey;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.DefaultAuthFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;
import java.security.KeyPair;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 21/02/13
 * Time: 1:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class UserAuthServiceClient extends UserAuthService<ClientSessionImpl> implements ServiceClient {

    private UserAuth userAuth;
    private AuthFuture authFuture;
    private final CloseFuture closeFuture;
    private enum State { Uninitialized, WaitForAuth, Authenticating, Authenticated }
    private State state = State.Uninitialized;

    public UserAuthServiceClient(ClientSessionImpl session, Object sessionLock, CloseFuture closeFuture) {
        super(session, sessionLock);
        this.closeFuture = closeFuture;
    }

    /**
     * ClientSession.waitFor() might be waiting for this service to go into WAIT_AUTH state so
     * need to wake everything up when ever this state changes.
     *
     * Perhaps it should instead modify auth-future so that it also signals when a further auth is required
     * or perhaps it should get the auth information using callbacks/polling?
     * @param state
     */
    private void nextState(State state) {
        this.state = state;
        this.sessionLock.notifyAll();
    }

    public void serverAcceptedService() {
        nextState(State.WaitForAuth);
        // Wake up anything that might be waiting for this to transition into the wait-for-auth state.
        this.sessionLock.notifyAll();
    }

    public boolean isWaitingForAuth() {
        return state == State.WaitForAuth;
    }

    public void process(SshConstants.Message cmd, Buffer buffer) throws Exception {
        switch (state) {
            case WaitForAuth:
                // We're waiting for the client to send an authentication request
                // TODO: handle unexpected incoming packets
                break;
            case Authenticating:
                if (userAuth == null) {
                    throw new IllegalStateException("State is userAuth, but no user auth pending!!!");
                }
                buffer.rpos(buffer.rpos() - 1);
                processUserAuth(buffer);
                break;
            case Uninitialized:
            case Authenticated:
                throw new IllegalStateException("State is " + state);
        }
    }

    public void close(boolean immediately) {
        if (authFuture != null && !authFuture.isDone()) {
            authFuture.setException(new SshException("Session is closed"));
        }
        session.closeIoSession(immediately);
    }

    private void verifyAuthSetup() {
        if (closeFuture.isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        if (state == State.Authenticated) {
            throw new IllegalStateException("Already authorized");
        }
        if (userAuth != null) {
            throw new IllegalStateException("A user authentication request is already pending");
        }
    }

    private AuthFuture waitForAuth() {
        session.waitFor(ClientSession.CLOSED | ClientSession.WAIT_AUTH, 0);
        if (closeFuture.isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        return new DefaultAuthFuture(sessionLock);
    }

    private void processUserAuth(Buffer buffer) throws IOException {

        switch (userAuth.next(buffer)) {
            case Success:
                authFuture.setAuthed(true);
                session.switchToNextService(true, userAuth.getUsername());
                nextState(State.Authenticated);
                break;
            case Failure:
                authFuture.setAuthed(false);
                userAuth = null;
                nextState(State.WaitForAuth);
                break;
            case Continued:
                nextState(State.Authenticating);
                break;
        }
    }

    public AuthFuture authAgent(String username) throws IOException {
        verifyAuthSetup();
        if (session.getFactoryManager().getAgentFactory() == null) {
            throw new IllegalStateException("No ssh agent factory has been configured");
        }
        authFuture = waitForAuth();
        userAuth = new UserAuthAgent(session, username);
        processUserAuth(null);
        return authFuture;
    }

    public AuthFuture authPassword(String username, String password) throws IOException {
        verifyAuthSetup();
        authFuture = waitForAuth();
        userAuth = new UserAuthPassword(session, username, password);
        processUserAuth(null);
        return authFuture;
    }

    public AuthFuture authPublicKey(String username, KeyPair key) throws IOException {
        verifyAuthSetup();
        authFuture = waitForAuth();
        userAuth = new UserAuthPublicKey(session, username, key);
        processUserAuth(null);
        return authFuture;
    }
}
