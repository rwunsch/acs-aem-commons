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

import com.adobe.acs.commons.userrequesttracker.core.UserRequestTrackingCore;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.ComponentContext;
import org.apache.sling.api.resource.LoginException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {UserRequestTrackingMBean.class},
    immediate = true,
    scope = ServiceScope.SINGLETON,
    property = {
        "jmx.objectname=com.adobe.acs.commons.userrequesttracker:type=UserRequestTracking",
        "service.runmode=author"
    }
)
public class UserRequestTracking implements UserRequestTrackingMBean {

    private static final Logger log = LoggerFactory.getLogger(UserRequestTracking.class);

    @Reference
    private UserRequestTrackingCore trackingCore;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Activate
    protected void activate(ComponentContext context) {
        log.debug("Activating UserRequestTracking MBean...");
    }

    @Deactivate
    protected void deactivate() {
        log.debug("Deactivating UserRequestTracking MBean...");
    }

    public CompositeData getRequestsPerUserLastMinute() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastMinute(), "Last Minute");
    }

    public CompositeData getRequestsPerUserLastHour() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastHour(), "Last Hour");
    }

    public CompositeData getRequestsPerUserLastDay() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastDay(), "Last Day");
    }

    public CompositeData getRequestsPerUserLastWeek() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastWeek(), "Last Week");
    }

    public CompositeData getRequestsPerUserLastMonth() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastMonth(), "Last Month");
    }

    public CompositeData getRequestsPerUserLastYear() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsLastYear(), "Last Year");
    }

    public CompositeData getRequestsPerUserForever() {
        return getCompositeDataFromMap(trackingCore.getRequestCountsForever(), "Forever");
    }

/*     public CompositeData getAllRequestsData() {
        try {
            CompositeType dataType = new CompositeType(
                "AllRequestsData",
                "Data for all request intervals",
                UserRequestTrackingCore.ITEM_NAMES,
                new String[]{
                    "Requests in the last minute", "Requests in the last hour", "Requests in the last day",
                    "Requests in the last week", "Requests in the last month", "Requests in the last year", "Requests forever"
                },
                new OpenType[]{SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG}
            );
    
            Object[] values = {
                trackingCore.getRequestCountsLastMinute().size(),
                trackingCore.getRequestCountsLastHour().size(),
                trackingCore.getRequestCountsLastDay().size(),
                trackingCore.getRequestCountsLastWeek().size(),
                trackingCore.getRequestCountsLastMonth().size(),
                trackingCore.getRequestCountsLastYear().size(),
                trackingCore.getRequestCountsForever().size()
            };
    
            return new CompositeDataSupport(dataType, dataType.keySet().toArray(new String[0]), values);
        } catch (OpenDataException e) {
            log.error("Failed to create CompositeData for all requests", e);
            return null;
        }
    } */


    @Override
    public TabularData fetchClusterWideUserRequestData() {
        log.debug("Retrieving full cluster-wide data for all timeframes.");
        Map<String, Map<String, Long>> aggregatedData = new TreeMap<>(new HashMap<>());
    
        try {
            int count = 1;
            for (String interval : UserRequestTrackingCore.ITEM_NAMES) {
                log.debug("Fetching cluster-wide data for the {}...", interval);
                Map<String, Long> userRequests = new TreeMap<>(trackingCore.getClusterWideData("RequestsPerUser" + interval));
                interval = count + " " + interval;
                aggregatedData.put(interval, userRequests);
                count += 1;
            }
            log.debug("Cluster-wide data retrieval successful: {} ",  aggregatedData);
        } catch (Exception e) {
            log.error("Error retrieving cluster-wide data", e);
            return null;
        }
    
        try {
            // Define the composite type for the inner table (username and request count)
            CompositeType userDataType = new CompositeType(
                "UserData",
                "User Request Data",
                new String[]{"_username", "requestCount"},
                new String[]{"Username", "Request Count"},
                new OpenType[]{SimpleType.STRING, SimpleType.LONG}
            );
    
            // Define the table type for the inner table
            TabularType userDataTabularType = new TabularType(
                "->",
                "Table of User Request Data",
                userDataType,
                new String[]{"_username"}
            );
    
            // Define the composite type for the outer table
            CompositeType outerType = new CompositeType(
                "TimeIntervalData",
                "Time Interval User Data",
                new String[]{"timeInterval", "userData"},
                new String[]{"Time Interval", "User Data"},
                new OpenType[]{SimpleType.STRING, userDataTabularType}
            );
    
            // Define the table type for the outer table
            TabularType outerTabularType = new TabularType(
                "TimeIntervalUserDataTable",
                "Table of Time Interval User Data",
                outerType,
                new String[]{"timeInterval"}
            );
    
            // Create the outer table
            TabularDataSupport outerTable = new TabularDataSupport(outerTabularType);
    
            for (Map.Entry<String, Map<String, Long>> entry : aggregatedData.entrySet()) {
                String timeInterval = entry.getKey();
                Map<String, Long> userRequests =  new TreeMap<>(entry.getValue());
    
                // Create the inner table for each time interval
                TabularDataSupport innerTable = new TabularDataSupport(userDataTabularType);
    
                for (Map.Entry<String, Long> userEntry : userRequests.entrySet()) {
                    String userId = userEntry.getKey();
                    Long requestCount = userEntry.getValue();
    
                    innerTable.put(new CompositeDataSupport(userDataType,
                        new String[]{"_username", "requestCount"},
                        new Object[]{userId, requestCount}));
                }
    
                // Add the time interval and inner table to the outer table
                outerTable.put(new CompositeDataSupport(outerType,
                    new String[]{"timeInterval", "userData"},
                    new Object[]{timeInterval, innerTable}));
            }
    
            return outerTable;
        } catch (OpenDataException e) {
            log.error("Failed to create TabularData for cluster-wide data", e);
            return null;
        }
    }

    @Override
    public Map<String, Map<String, Long>> fetchClusterWideUserRequestDataAsMap() {
        log.debug("Retrieving full cluster-wide data as a map for all timeframes.");
        Map<String, Map<String, Long>> aggregatedData = new TreeMap<>(new HashMap<>());

        try {
            int count = 1;
            for (String interval : UserRequestTrackingCore.ITEM_NAMES) {
                log.debug("Fetching cluster-wide data for the {}...", interval);
                Map<String, Long> userRequests = new TreeMap<>(trackingCore.getClusterWideData("RequestsPerUser" + interval));
                interval = count + " " + interval;
                aggregatedData.put(interval, userRequests);
                count += 1;
            }
            log.debug("Cluster-wide data retrieval as map successful: {} ", aggregatedData);
        } catch (Exception e) {
            log.error("Error retrieving cluster-wide data as map", e);
            return null;
        }

        return aggregatedData;
    }

@Override
public Map<String, Map<String, Map<String, Long>>> fetchAllInstancesDataForAllIntervalsAsMap() {
    log.debug("Retrieving data for all instances and all intervals.");
    Map<String, Map<String, Map<String, Long>>> allInstancesData = new HashMap<>();
    
    Map<String, Object> authInfo = new HashMap<>();
    authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);

    try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
        Resource instancesResource = resolver.getResource(UserRequestTrackingCore.INSTANCE_PATH);
        if (instancesResource == null) {
            log.warn("Instances resource not found at path: {}", UserRequestTrackingCore.INSTANCE_PATH);
            return allInstancesData;
        }

        for (Resource instance : instancesResource.getChildren()) {
            String instanceId = instance.getName();
            log.debug("Processing instance: {}", instanceId);

            Map<String, Map<String, Long>> instanceData = new HashMap<>();

            int count = 1;
            for (String interval : UserRequestTrackingCore.ITEM_NAMES) {
                Resource intervalResource = instance.getChild("RequestsPerUser" + interval);
                if (intervalResource != null) {
                    Map<String, Long> intervalData = new HashMap<>();

                    // Use ValueMap to retrieve properties
                    ValueMap properties = intervalResource.getValueMap();
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        String userId = entry.getKey();
                        Object value = entry.getValue();

                        if (value instanceof Long) {
                            intervalData.put(userId, (Long) value);
                        } else if (value instanceof Number) {
                            intervalData.put(userId, ((Number) value).longValue());
                        }
                        log.debug("Retrieved user {} with count {} for interval {} in instance {}", userId, value, interval, instanceId);
                    }
                    interval = count + " " + interval;
                    instanceData.put(interval, intervalData);
                    count += 1;
                } else {
                    log.debug("No data found for interval {} in instance {}", interval, instanceId);
                }
            }

            allInstancesData.put(instanceId, instanceData);
        }
    } catch (LoginException e) {
        log.error("Error retrieving data for all instances and all intervals", e);
    }

    log.debug("All instances data: {}", allInstancesData);
    return allInstancesData;
}


    @Override
    public void resetRequestCounts() {
        trackingCore.resetRequestCounts();
    }

    private CompositeData getCompositeDataFromMap(Map<String, Long> requestCountsMap, String period) {
        log.debug("Fetching request counts for the {}...", period);
        try {
            if (requestCountsMap.isEmpty()) {
                log.debug("No request counts available for the {}", period);
                return null; // Return null or handle it as an empty composite
            }
    
            String[] dynamicItemNames = requestCountsMap.keySet().toArray(new String[0]);
            String[] dynamicItemDescriptions = new String[dynamicItemNames.length];
            OpenType<?>[] dynamicItemTypes = new OpenType<?>[dynamicItemNames.length];
            Object[] itemValues = new Object[dynamicItemNames.length];
    
            for (int i = 0; i < dynamicItemNames.length; i++) {
                dynamicItemDescriptions[i] = "Request count for " + dynamicItemNames[i];
                dynamicItemTypes[i] = SimpleType.LONG;
                itemValues[i] = requestCountsMap.get(dynamicItemNames[i]);
            }
    
            CompositeType dynamicCompositeType = new CompositeType(
                    "->",
                    "Composite data type for user request counts for the " + period,
                    dynamicItemNames,
                    dynamicItemDescriptions,
                    dynamicItemTypes
            );
    
            return new CompositeDataSupport(dynamicCompositeType, dynamicItemNames, itemValues);
        } catch (OpenDataException e) {
            log.error("Failed to create CompositeData for {} data", period, e);
            return null;
        }
    }


}
