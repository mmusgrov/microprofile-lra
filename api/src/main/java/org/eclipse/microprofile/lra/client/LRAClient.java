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

package org.eclipse.microprofile.lra.client;

import org.eclipse.microprofile.lra.annotation.CompensatorStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface LRAClient {

    /**
     * Explicitly dispose of all resources. After this call the instance may no
     * longer be useable
     */
    void close();

    /**
     * Start a new LRA
     *
     * @param parentLRA The parent of the LRA that is about to start. If null then
     *                  the new LRA will be top level
     * @param clientID  The client may provide a (preferably) unique identity which
     *                  will be reported back when the LRA is queried.
     * @param timeout   Specifies the maximum time that the LRA will exist for. If the
     *                  LRA is terminated because of a timeout it will be cancelled.
     * @param unit      Specifies the unit that the timeout is measured in
     * @return the identifier of the new LRA
     * @throws LRANotFoundException   if the parent LRA is known to no longer exist
     * @throws GenericLRAException a new LRA could not be started. The specific
     *                             reason is available in {@link GenericLRAException#getStatusCode()}
     */
    LRAId startLRA(LRAId parentLRA, String clientID, Long timeout, TimeUnit unit)
            throws GenericLRAException;

    /**
     * Start a top level LRA (ie similar to
     * {@link LRAClient#startLRA(LRAId, String, Long, TimeUnit)}
     *
     * @param clientID The client may provide a (preferably) unique identity which
     *                 will be reported back when the LRA is queried.
     * @param timeout  Specifies the maximum time that the LRA will exist for. If the
     *                 LRA is terminated because of a timeout it will be cancelled.
     * @param unit     Specifies the unit that the timeout is measured in
     * @return the identifier of the new LRA
     * @throws LRANotFoundException   if the parent LRA is known to no longer exist
     * @throws GenericLRAException a new LRA could not be started. The specific
     *                             reason is available in {@link GenericLRAException#getStatusCode()}
     */
    LRAId startLRA(String clientID, Long timeout, TimeUnit unit)
            throws GenericLRAException;

    /**
     * Attempt to cancel an LRA
     * <p>
     * Trigger compensation of all participants enlisted with the LRA (ie the
     * compensate message will be sent to each participant).
     *
     * @param lraId The unique identifier of the LRA (required)
     *              <p>
     *              {@link CompensatorStatus#name()}. If the final status is not returned the
     *              client can still discover the final state using the
     *              {@link LRAClient#getStatus(LRAId)} method
     * @return the response MAY contain the final status of the LRA as reported by
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException Communication error (the reason is availalbe via
     *                             the {@link GenericLRAException#getStatusCode()} method
     */
    String cancelLRA(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Attempt to close an LRA
     * <p>
     * Tells the LRA to close normally. All participants will be triggered by the
     * coordinator (ie the complete message will be sent to each participant).
     *
     * @param lraId The unique identifier of the LRA (required)
     *              <p>
     *              {@link CompensatorStatus#name()}. If the final status is not returned the
     *              client can still discover the final state using the
     *              {@link LRAClient#getStatus(LRAId)} method
     * @return the response MAY contain the final status of the LRA as reported by
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException Communication error (the reason is availalbe via
     *                             the {@link GenericLRAException#getStatusCode()} method
     */
    String closeLRA(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Return detailed information corresponding to an LRA id
     *
     * @param lraId the id of the LRA
     * @return full details of the requested LRA
     * @throws GenericLRAException if there was an error getting the requested
     *                             information from the LRA coordinator
     */
    LRAInfo getLRAInfo(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Set the connect timeout for subsequent client requests.
     * Value 0 represents infinity. Negative values are not allowed.
     *
     * @param connect he maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @throws IllegalArgumentException - when the value is negative
     */
    void connectTimeout(long connect, TimeUnit unit);

    /**
     * Set the read timeout for subsequent client requests.
     * Value 0 represents infinity. Negative values are not allowed.
     *
     * @param read he maximum time to wait
     * @param unit the time unit of the timeout argument
     * @throws IllegalArgumentException - when the value is negative
     */

    void readTimeout(long read, TimeUnit unit);

    /**
     * Lookup active LRAs
     *
     * @return a list of active LRAs
     * @throws GenericLRAException on error
     */
    List<LRAInfo> getActiveLRAs() throws GenericLRAException;

    /**
     * Returns all (both active and recovering) LRAs
     *
     * @return a list of all LRAs known to this coordinator
     * @throws GenericLRAException on error
     */
    List<LRAInfo> getAllLRAs() throws GenericLRAException;

    /**
     * List recovering Long Running Actions
     *
     * @return LRAs that are recovering (ie the participant is still
     * attempting to complete or compensate
     * @throws GenericLRAException on error
     */
    List<LRAInfo> getRecoveringLRAs() throws GenericLRAException;

    /**
     * Lookup the status of an LRA
     *
     * @param lraId the LRA whose status is being requested
     * @return the status or empty if the the LRA is still active (ie has not yet
     * been closed or cancelled)
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()}
     *                             may provide a more specific reason.
     */
    Optional<CompensatorStatus> getStatus(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Indicates whether an LRA is active. The same information can be obtained via
     * a call to {@link LRAClient#getStatus(LRAId)}.
     *
     * @param lraId The unique identifier of the LRA (required)
     * @return whether or not the specified LRA is active
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()}
     *                             may provide a more specific reason.
     */
    Boolean isActiveLRA(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Indicates whether an LRA was compensated. The same information can be
     * obtained via a call to {@link LRAClient#getStatus(LRAId)}.
     *
     * @param lraId The unique identifier of the LRA (required)
     * @return whether or not the specified LRA has been compensated
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()}
     *                             may provide a more specific reason.
     */
    Boolean isCompensatedLRA(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Indicates whether an LRA is complete. The same information can be obtained
     * via a call to {@link LRAClient#getStatus(LRAId)}.
     *
     * @param lraId The unique identifier of the LRA (required)
     * @return whether or not the specified LRA has been completed
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()}
     *                             may provide a more specific reason.
     */
    Boolean isCompletedLRA(LRAId lraId) throws LRANotFoundException, GenericLRAException;

    /**
     * Join an LRA passing in a class that will act as the participant.
     * The class should contain the LRA protocol annotations:
     *                       {@link org.eclipse.microprofile.lra.annotation.Compensate}, etc
     *
     * @param lraId           The unique identifier of the LRA (required)
     * @param beanClass   An annotated class for the participant methods:
     *                        {@link org.eclipse.microprofile.lra.annotation.Compensate}, etc.
     * @param compensatorData Compensator specific data that the coordinator will
     *                        pass to the participant when the LRA is closed or
     *                        cancelled
     * @return a recovery URL for this enlistment
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()} may provide a more specific reason.
     */
    String joinLRA(LRAId lraId, Class<?> beanClass, String compensatorData)
            throws LRANotFoundException, GenericLRAException;

    /**
     * A Compensator can resign from the LRA at any time prior to the completion
     * of an activity
     *
     * @param lraId The unique identifier of the LRA (required)
     * @param body  (optional)
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws LRANotFoundException   if the LRA no longer exists
     * @throws GenericLRAException if the request to the coordinator failed.
     *                             {@link GenericLRAException#getCause()} and/or
     *                             {@link GenericLRAException#getStatusCode()} may provide a more specific reason.
     */
    void leaveLRA(LRAId lraId, String body) throws LRANotFoundException, GenericLRAException;

    /**
     * LRAs can be created with timeouts after which they are cancelled. Use this
     * method to update the timeout.
     *
     * @param lraId the id of the lra to update
     * @param limit the new timeout period
     * @param unit  the time unit for limit
     * @throws LRANotFoundException if the LRA no longer exists
     */
    void renewTimeLimit(LRAId lraId, long limit, TimeUnit unit) throws LRANotFoundException;

    /**
     * checks whether there is an LRA associated with the calling thread.
     *
     * @return the current LRA (can be null)
     */
    LRAId getCurrent();

    /**
     * Update the clients notion of the current coordinator.
     *
     * @param lraId the id of the LRA (can be null)
     * @throws LRANotFoundException if the LRA no longer exists
     */
    void setCurrentLRA(LRAId lraId);
}
