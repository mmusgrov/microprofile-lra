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
 * Participants join LRAs by marking a bean method (or class) with the {@link LRA}
 * annotation, indicating which methods to call during completion using the
 * {@link Compensate} and {@link Complete} annotations. If any method in the bean
 * is annotated with {@link ParticipantData} then the method will be called
 * during registration time and any return value will be stored with the LRA
 * coordinator. This data will be returned to the participant whenever any of the
 * participant callbacks are invoked.
 *
 * The mechanism by which this data is passed to the particpant is specified in
 * the protocol specific callbacks (for example, a JAX-RS praticipant will be
 * given the data in the body of the request using MediaType.APPLICATION_JSON).
 *
 * If the annotation is applied to multiple methods an arbitrary one is chosen.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ParticipantData {
}
