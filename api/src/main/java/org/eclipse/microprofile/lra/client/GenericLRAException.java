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

public class GenericLRAException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private LRAId lraId;
    private int statusCode;

    /**
     * a transport specific status code. For example in a JAX-RS based implementation
     * it would correspond to an HTTP status code.
     *
     * @return a transport specific status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    public LRAId getLraId() {
        return lraId;
    }

    public GenericLRAException(LRAId lraId, int statusCode, String message,
                               Throwable cause) {
        super(String.format("%s: %s", lraId, message), cause);

        this.lraId = lraId;
        this.statusCode = statusCode;
    }
}
