package com.atlassian.connect.play.java.service;

import com.atlassian.connect.play.java.AcHost;

import static play.libs.F.Promise;

/**
 * A service for activities related to an Atlassian application host
 */
public interface AcHostService {

    /**
     * Retrieves the public key from the remote host (via a REST url)
     *
     *
     * @param acHost
     * @return
     */
    Promise<String> fetchPublicKeyFromRemoteHost(AcHost acHost);

    /**
     * Registers a new host, or updates the details from an existing host
     *
     *
     * @param acHost
     * @return
     */
    Promise<Void> registerHost(AcHost acHost);
}