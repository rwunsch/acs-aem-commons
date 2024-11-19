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

package com.adobe.acs.commons.userrequesttracker.mbeans;

import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

public interface UserRequestTrackingMBean {

    CompositeData getRequestsPerUserLastMinute();

    CompositeData getRequestsPerUserLastHour();

    CompositeData getRequestsPerUserLastDay();

    CompositeData getRequestsPerUserLastWeek();

    CompositeData getRequestsPerUserLastMonth();

    CompositeData getRequestsPerUserLastYear();

    CompositeData getRequestsPerUserForever();

    //CompositeData getAllRequestsData();

    void resetRequestCounts();

    TabularData fetchClusterWideUserRequestData();

    Map<String, Map<String, Long>> fetchClusterWideUserRequestDataAsMap();

    Map<String, Map<String, Map<String, Long>>> fetchAllInstancesDataForAllIntervalsAsMap();

}
