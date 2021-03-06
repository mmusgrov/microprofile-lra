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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a participant is unable to compensate it must remember the fact (by
 * reporting it when asked for its' {@link Status}) until it is explicitly
 * told to forget. To support this requirement the developer should annotate
 * one of the participant methods with @Forget.
 *
 * The LRA context is made available to the method in a header with the name
 * {@link org.eclipse.microprofile.lra.client.LRAClient#LRA_HTTP_HEADER}
 * provided the status method is annotated with @LRA(LRA.Type.SUPPORTS)
 *
 * The coordinator will invoke the forget method with best effort semantics.
 * The participant can autonomously perform forget actions by querying the
 * coordinator and if the LRA no longer exists it can safely forget about
 * this LRA.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Forget {
}
