package org.apache.sshd.common.service;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.future.CloseFuture;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 20/02/13
 * Time: 1:18 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ServiceClient extends Service {

    /**
     * A request for the service has been sent to the server.
     *
     * Is this useful?  If a service can start sending data early then this would help.
     */
    // ServiceClient requested();

    /**
     * Call this when the service has been serverAcceptedService.
     *
     * ClientSessionImpl creates the first service before it has properly connected so that calling code can
     * start manipulating the services state before the service has been requested, yet alone serverAcceptedService.
     * For instance, by trying to set the authPassword that will be used.
     */
    void serverAcceptedService();

}
