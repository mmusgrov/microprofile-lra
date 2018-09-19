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

import org.eclipse.microprofile.lra.client.InvalidLRAException;
import org.eclipse.microprofile.lra.client.LRAId;

import java.net.MalformedURLException;
import java.net.URL;

public class LRAUrl extends LRAId {
    private URL endpoint;

    public LRAUrl(URL endpoint) {
        super(endpoint.toExternalForm());

        this.endpoint = endpoint;
    }

    public LRAUrl(String stringForm) throws InvalidLRAException {
        this(toURL(stringForm));
    }

    public URL getEndpoint() {
        return endpoint;
    }

    private static URL toURL(String stringForm) throws InvalidLRAException {
        try {
            return new URL(stringForm);
        } catch (MalformedURLException e) {
            throw new InvalidLRAException(stringForm, "Cannot convert to a URL", e);
        }
    }
}
