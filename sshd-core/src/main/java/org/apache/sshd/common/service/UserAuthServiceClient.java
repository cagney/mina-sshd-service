package org.apache.sshd.common.service;

import org.apache.sshd.client.UserAuth;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.DefaultAuthFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 21/02/13
 * Time: 1:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class UserAuthServiceClient extends UserAuthService<ClientSessionImpl> implements ServiceClient {

    /**
     * When !authFuture.isDone() the current authentication
     */
    private UserAuth userAuth;

    /**
     * The AuthFuture that is being used by the current auth request.  This encodes the state.
     * isSuccess -> authenticated, else if isDone -> server waiting for user auth, else authenticating.
     */
    private AuthFuture authFuture;

    public UserAuthServiceClient(ClientSessionImpl session, Object sessionLock) {
        super(session, sessionLock);
        // start with a failed auth future?
        this.authFuture = new DefaultAuthFuture(sessionLock);
        logger.debug("created");
    }

    public void start() {
        synchronized (sessionLock) {
            logger.debug("accepted");
            // kick start the authentication process by failing the pending auth.
            this.authFuture.setAuthed(false);
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

    private boolean readyForAuth(UserAuth nextUserAuth) {
        // isDone indicates that the last auth finished and a new one can commence.
        while (!this.authFuture.isDone()) {
            logger.debug("waiting to send authentication");
            try {
                this.authFuture.await();
            } catch (InterruptedException e) {
                logger.debug("Unexpected interrupt", e);
                throw new RuntimeException(e);
            }
        }
        if (this.authFuture.isSuccess()) {
            logger.debug("already authenticated");
            throw new IllegalStateException("Already authenticated");
        }
        if (this.authFuture.getException() != null) {
            logger.debug("probably closed", this.authFuture.getException());
            return false;
        }
        if (!this.authFuture.isFailure()) {
            logger.debug("unexpected state");
            throw new IllegalStateException("Unexpected authentication state");
        }
        if (this.userAuth != null) {
            logger.debug("authentication already in progress");
            throw new IllegalStateException("Authentication already in progress?");
        }
        // Set up the next round of authentication.  Each round gets a new lock.
        this.userAuth = nextUserAuth;
        this.authFuture = new DefaultAuthFuture(sessionLock);
        logger.debug("ready to authenticate using {} with new lock", nextUserAuth);
        return true;
    }

    private void processUserAuth(Buffer buffer) throws IOException {
        logger.debug("processing {}", userAuth);
        switch (userAuth.next(buffer)) {
            case Success:
                logger.debug("succeeded with {}", userAuth);
                session.switchToService(true, userAuth.getUsername(), userAuth.getService());
                authFuture.setAuthed(true);
                break;
            case Failure:
                logger.debug("failed with {}", userAuth);
                this.userAuth = null;
                this.authFuture.setAuthed(false);
                break;
            case Continued:
                logger.debug("continuing with {}", userAuth);
                break;
        }
    }

    public AuthFuture auth(UserAuth userAuth) throws IOException {
        logger.debug("will try authentication with {}", userAuth);
        synchronized (sessionLock) {
            if (readyForAuth(userAuth)) {
                processUserAuth(null);
            }
            return authFuture;
        }
    }
}
