/*
 *******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.eclipse.microprofile.lra.client.http;

import org.eclipse.microprofile.lra.client.GenericLRAException;
import org.eclipse.microprofile.lra.client.LRAClient;
import org.eclipse.microprofile.lra.client.LRAId;

import javax.ws.rs.NotFoundException;
import java.net.URI;
import java.net.URL;

public interface LRAHttpClient extends LRAClient {
    /**
     * the name of the HTTP header field that contains the LRA id associated with
     * a request/response
     */
    String LRA_HTTP_HEADER = "Long-Running-Action";
    /**
     * the name of the HTTP header field that contains a recovery URL corresponding
     * to a participant
     * enlistment in an LRA {@link LRAHttpClient#updateCompensator}
     */
    String LRA_HTTP_RECOVERY_HEADER = "Long-Running-Action-Recovery";
    /**
     * Key for looking up the config property that specifies which host a
     * coordinator is running on
     */
    String LRA_COORDINATOR_HOST_KEY = "lra.http.host";
    /**
     * Key for looking up the config property that specifies which port a
     * coordinator is running on
     */
    String LRA_COORDINATOR_PORT_KEY = "lra.http.port";
    /**
     * Key for looking up the config property that specifies which JAX-RS path a
     * coordinator is running on
     */
    String LRA_COORDINATOR_PATH_KEY = "lra.coordinator.path";
    /**
     * Key for looking up the config property that specifies which JAX-RS path a
     * recovery coordinator is running on
     */
    String LRA_RECOVERY_HOST_KEY = "lra.http.recovery.host";
    /**
     * Key for looking up the config property that specifies which JAX-RS path a
     * recovery coordinator is running on
     */
    String LRA_RECOVERY_PORT_KEY = "lra.http.recovery.port";
    /**
     * Key for looking up the config property that specifies which JAX-RS path a
     * recovery coordinator is running on
     */
    String LRA_RECOVERY_PATH_KEY = "lra.coordinator.recovery.path";


    /**
     * Set the endpoint on which the coordinator is available
     *
     * @param uri the url of the LRA coordinator
     */
    void setCoordinatorURI(URI uri);

    /**
     * Set the endpoint on which the recovery coordinator is available
     *
     * @param uri the url of the LRA recovery coordinator
     */
    void setRecoveryCoordinatorURI(URI uri);

    /**
     * Join an LRA passing in a class that will act as the participant.
     * Similar to {@link LRAHttpClient#joinLRA(LRAId, Class, URI, String)} except
     * that the various participant URLs are expressed as CDI annotations on
     * the passed in resource class.
     *
     * @param lraId           The unique identifier of the LRA (required)
     * @param resourceClass   An annotated class for the participant methods:
     *                        {@link org.eclipse.microprofile.lra.annotation.Compensate}, etc.
     * @param baseUri         Base uri for the participant endpoints
     * @param compensatorData Compensator specific data that the coordinator will
     *                        pass to the participant when the LRA is closed or
     *                        cancelled
     * @return a recovery URL for this enlistment
     * @throws NotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()} may provide a more specific reason.
     */

    String joinLRA(LRAId lraId, Class<?> resourceClass, URI baseUri, String compensatorData)
            throws GenericLRAException;
    /**
     * A participant can join with the LRA at any time prior to the completion of
     * an activity. The participant provides end points on which it will listen
     * for LRA related events.
     *
     * @param lraId           The unique identifier of the LRA (required) to enlist with
     * @param timelimit       The time limit (in seconds) that the participant can
     *                        guarantee that it can compensate the work performed while
     *                        the LRA is active.
     * @param compensateUrl   the `compensatation URL`
     * @param completeUrl     the `completion URL`
     * @param forgetUrl       the `forget URL`
     * @param leaveUrl        the `leave URL`
     * @param statusUrl       the `status URL
     * @param compensatorData data that will be stored with the coordinator and
     *                        passed back to the participant when the LRA is closed
     *                        or cancelled
     * @return a recovery URL for this enlistment
     * @throws NotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()}
     *                             may provide a more specific reason.
     */
    String joinLRA(LRAId lraId, Long timelimit,
                   URL compensateUrl, URL completeUrl, URL forgetUrl,
                   URL leaveUrl, URL statusUrl,
                   String compensatorData) throws GenericLRAException;

    /**
     * Change the endpoints that a participant can be contacted on.
     *
     * @param recoveryUrl     the recovery URL returned from a participant join request
     * @param compensateUrl   the URL to invoke when the LRA is cancelled
     * @param completeUrl     the URL to invoke when the LRA is closed
     * @param statusUrl       if a participant cannot finish immediately then it provides
     *                        this URL that the coordinator uses to monitor the progress
     * @param forgetUrl       used to inform the participant that can forget about this LRA
     * @param compensatorData opaque data that returned to the participant when the
     *                        LRA is closed or cancelled
     * @return an updated recovery URL for this participant
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()} may provide a more specific reason.
     */
    URL updateCompensator(URL recoveryUrl, URL compensateUrl, URL completeUrl,
                          URL forgetUrl, URL statusUrl,
                          String compensatorData) throws GenericLRAException;
}
