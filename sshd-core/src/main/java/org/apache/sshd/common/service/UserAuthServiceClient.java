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
    private final CloseFuture closeFuture;

    /**
     * The AuthFuture that is being used by the current auth request.  This encodes the state.
     * isSuccess -> authenticated, else if isDone -> server waiting for user auth, else authenticating.
     */
    private volatile AuthFuture authFuture;



    public UserAuthServiceClient(ClientSessionImpl session, Object sessionLock, CloseFuture closeFuture) {
        super(session, sessionLock);
        this.closeFuture = closeFuture;
        // start with a failed auth future?
        this.authFuture = new DefaultAuthFuture(sessionLock);
        logger.debug("created");
    }

    public void serverAcceptedService() {
        synchronized (sessionLock) {
            logger.debug("accepted");
            // kick start the authentication process by failing the pending auth.
            this.authFuture.setAuthed(false);
        }
    }

    /**
     * Is the server waiting on the client to provide some sort of authentication?
     * @return
     */
    public boolean isWaitingForAuth() {
        synchronized (sessionLock) {
            return authFuture.isFailure();
        }
    }

    public void process(SshConstants.Message cmd, Buffer buffer) throws Exception {
        synchronized (sessionLock) {
            if (this.authFuture.isSuccess()) {
                logger.debug("illegal state");
                throw new IllegalStateException("UserAuth message delivered to authenticated client");
            } else if (this.authFuture.isDone()) {
                logger.debug("ignoring random message");
               // ignore for now; TODO: random packets
            } else {
                buffer.rpos(buffer.rpos() - 1);
                processUserAuth(buffer);
            }
        }
    }

    public void close(boolean immediately) {
        synchronized (sessionLock) {
            if (!authFuture.isDone()) {
                authFuture.setException(new SshException("Session is closed"));
            }
            session.closeIoSession(immediately);
        }
    }

    private void waitForAuth() {
        // isDone indicates that the last auth finished and a new one can commence.
        while (!this.authFuture.isDone()) {
            logger.debug("waiting to send authentication");
            try {
                this.authFuture.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (this.closeFuture.isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        if (this.authFuture.isSuccess()) {
            throw new IllegalStateException("Already authenticated");
        }
        if (this.authFuture.isCanceled()) {
            throw new IllegalStateException("A user authentication request was canceled");
        }
        if (!this.authFuture.isFailure()) {
            throw new IllegalStateException("Unexpected authentication state");
        }
        if (this.userAuth != null) {
            throw new IllegalStateException("Authentication already in progress?");
        }
        logger.debug("ready to try authentication with new lock");
        // The new future !isDone() - i.e., in progress blocking out other waits.
        this.authFuture = new DefaultAuthFuture(sessionLock);
    }

    private void processUserAuth(Buffer buffer) throws IOException {
        logger.debug("processing {}", userAuth);
        switch (userAuth.next(buffer)) {
            case Success:
                logger.debug("succeeded with {}", userAuth);
                session.switchToNextService(true, userAuth.getUsername());
                authFuture.setAuthed(true);
                // also need to wake up waitFor(int,long)
                sessionLock.notifyAll();
                break;
            case Failure:
                logger.debug("failed with {}", userAuth);
                this.userAuth = null;
                this.authFuture.setAuthed(false);
                // also need to wake up waitFor(int,long)
                sessionLock.notifyAll();
                break;
            case Continued:
                logger.debug("continuing with {}", userAuth);
                break;
        }
    }

    public AuthFuture authAgent(String username) throws IOException {
        logger.debug("will try authentication with agent");
        synchronized (sessionLock) {
            waitForAuth();
            if (session.getFactoryManager().getAgentFactory() == null) {
                throw new IllegalStateException("No ssh agent factory has been configured");
            }
            userAuth = new UserAuthAgent(session, username);
            processUserAuth(null);
            return authFuture;
        }
    }

    public AuthFuture authPassword(String username, String password) throws IOException {
        logger.debug("will try authentication with username/password");
        synchronized (sessionLock) {
            waitForAuth();
            userAuth = new UserAuthPassword(session, username, password);
            processUserAuth(null);
            return authFuture;
        }
    }

    public AuthFuture authPublicKey(String username, KeyPair key) throws IOException {
        logger.debug("will try authentication with public-key");
        synchronized (sessionLock) {
            waitForAuth();
            userAuth = new UserAuthPublicKey(session, username, key);
            processUserAuth(null);
            return authFuture;
        }
    }
}
