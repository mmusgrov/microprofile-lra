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

package org.eclipse.microprofile.lra.annotation;

import org.eclipse.microprofile.lra.client.LRAClient;

import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Participants that have joined an LRA will need the LRA context in order
 * to respond to {@link Compensate}, {@link Complete}, {@link Status} and
 * {@link Forget} requests. Similarly, business logic annotated with
 * the {@link LRA} annotation may need to know whether or not they have
 * been invoked with an active context. Methods marked with any of these
 * annotations can obtain the context by providing a method parameter of
 * type {@link org.eclipse.microprofile.lra.client.LRAId} marked with the
 * {@link LRAContext} annotation.
 *
 * Note that this is not the only way to obtain the current context.
 * Other mechanisms include {@link LRAClient#getCurrent()} and transport
 * specific techniques (for example a JAX-RS annotated resource it would
 * be appropriate to inject it via a HeaderParam).
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface LRAContext {
}
