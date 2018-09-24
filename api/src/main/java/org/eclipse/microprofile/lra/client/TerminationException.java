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

public class TerminationException extends RuntimeException {
    private CompensatorStatus status;

    /**
     * Used by participants to report failure to compensate or complete.
     *
     * If the participant wants to report that completion is in progress then it should set the
     * status to {@link CompensatorStatus#Completing} and similarly for compensation calls.
     *
     * If the participant wants to report that it can never complete then it should set the
     * status to {@link CompensatorStatus#FailedToComplete} and similarly for compensation calls.
     *
     * It is an error condition for a participant to be asked to:
     *
     * - complete when it has already completed
     * - complete when it has already been asked to compensate
     * - compensate when it has already compensated
     * - compensate when it has already been asked to complete
     *
     * If the participant detects any of these conditions the it should report the
     * the fact by setting the status appropriately.
     *
     * @param status the current status of the participant
     * @param message a textual explanation of why the participant failed to compensate/complete
     */
    public TerminationException(CompensatorStatus status, String message) {
        super(message);

        this.status = status;
    }

    public CompensatorStatus getStatus() {
        return status;
    }
}
