/*
 * ACS AEM Commons
 *
 * Copyright (C) 2013 - 2023 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adobe.acs.commons.userrequesttracker.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "User Request Tracking and Persistence Service Configuration",
    description = "Configuration for scheduling intervals on the tracking cadence and if it should be enabled."
)
public @interface UserRequestPersistenceServiceConfig {

    int DEFAULT_INTERVAL = 1;

    /**
     * Scheduler interval in minutes.
     */
    @AttributeDefinition(
        name = "Scheduler Interval",
        description = "Scheduler interval in minutes. Default is 1 minute. Only change if you know what you are doing."
    )
    int scheduler_interval() default DEFAULT_INTERVAL;

    /**
     * Enable or disable the persistence task.
     */
    @AttributeDefinition(
        name = "Enabled",
        description = "Enable or disable the persistence task."
    )
    boolean enabled() default true;
}
