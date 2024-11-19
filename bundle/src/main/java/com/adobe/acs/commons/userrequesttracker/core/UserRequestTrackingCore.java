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

package com.adobe.acs.commons.userrequesttracker.core;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.HashMap;
import java.util.Map;

@Component(service = UserRequestTrackingCore.class, immediate = true)
public class UserRequestTrackingCore {

    private static final Logger log = LoggerFactory.getLogger(UserRequestTrackingCore.class);
    
    private final ConcurrentHashMap<String, Long> requestCountsLastMinute = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsLastHour = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsLastDay = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsLastWeek = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsLastMonth = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsLastYear = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> requestCountsForever = new ConcurrentHashMap<>();

    public static final String SERVICE_USER = "user-request-persistence-service";
    public static final String BASE_PATH = "/var/analytics/user-tracking";
    public static final String INSTANCE_PATH = BASE_PATH + "/instances";
    public static final String CLUSTER_PATH = BASE_PATH + "/cluster";
    public static final String[] ITEM_NAMES = {"LastMinute", "LastHour", "LastDay", "LastWeek", "LastMonth", "LastYear", "Forever"};

    @Reference
    private ResourceResolverFactory resolverFactory;

    public Map<String, Long> getRequestCountsLastMinute() {
        return requestCountsLastMinute;
    }
    public Map<String, Long> getRequestCountsLastHour() {
        return requestCountsLastHour;
    }
    public Map<String, Long> getRequestCountsLastDay() {
        return requestCountsLastDay;
    }
    public Map<String, Long> getRequestCountsLastWeek() {
        return requestCountsLastWeek;
    }
    public Map<String, Long> getRequestCountsLastMonth() {
        return requestCountsLastMonth;
    }
    public Map<String, Long> getRequestCountsLastYear() {
        return requestCountsLastYear;
    }
    public Map<String, Long> getRequestCountsForever() {
        return requestCountsForever;
    }

    public void setRequestCountsLastMinute(Map<String, Long> requestCountsLastMinuteSetter) {
        requestCountsLastMinute.clear(); 
        requestCountsLastMinute.putAll(requestCountsLastMinuteSetter);
    }
    public void setRequestCountsLastHour(Map<String, Long> requestCountsLastHourSetter) {
        requestCountsLastHour.clear(); 
        requestCountsLastHour.putAll(requestCountsLastHourSetter);
    }
    public void setRequestCountsLastDay(Map<String, Long> requestCountsLastDaySetter) {
        requestCountsLastDay.clear(); 
        requestCountsLastDay.putAll(requestCountsLastDaySetter);
    }
    public void setRequestCountsLastWeek(Map<String, Long> requestCountsLastWeekSetter) {
        requestCountsLastWeek.clear(); 
        requestCountsLastWeek.putAll(requestCountsLastWeekSetter);
    }
    public void setRequestCountsLastMonth(Map<String, Long> requestCountsLastMonthSetter) {
        requestCountsLastMonth.clear(); 
        requestCountsLastMonth.putAll(requestCountsLastMonthSetter);
    }
    public void setRequestCountsLastYear(Map<String, Long> requestCountsLastYearSetter) {
        requestCountsLastYear.clear(); 
        requestCountsLastYear.putAll(requestCountsLastYearSetter);
    }
    public void setRequestCountsForever(Map<String, Long> requestCountsForeverSetter) {
        requestCountsForever.clear(); 
        requestCountsForever.putAll(requestCountsForeverSetter);
    }

    public void mergeRequestCountsLastMinute(String userName, Long value,  BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        requestCountsLastMinute.merge(userName, value, remappingFunction);
    }

    public void clearRequestCountsLastMinute() {
        requestCountsLastMinute.clear(); 
    }
    public void clearRequestCountsLastHour() {
        requestCountsLastHour.clear(); 
    }
    public void clearRequestCountsLastDay() {
        requestCountsLastDay.clear(); 
    }
    public void clearRequestCountsLastWeek() {
        requestCountsLastWeek.clear(); 
    }
    public void clearRequestCountsLastMonth() {
        requestCountsLastMonth.clear(); 
    }
    public void clearRequestCountsLastYear() {
        requestCountsLastYear.clear(); 
    }
    public void clearRequestCountsForever() {
        requestCountsForever.clear(); 
    }

    public void resetRequestCounts() {
        log.info("Resetting all request count data...");
        requestCountsLastMinute.clear();
        requestCountsLastHour.clear();
        requestCountsLastDay.clear();
        requestCountsLastWeek.clear();
        requestCountsLastMonth.clear();
        requestCountsLastYear.clear();
        requestCountsForever.clear();
        log.debug("Request counts reset.");
    }

    public Map<String, Long> getClusterWideData(String interval) {
        log.info("Retrieving cluster-wide data for interval: {}", interval);
        Map<String, Long> clusterData = new HashMap<>();
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, SERVICE_USER);
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                log.error("Failed to obtain a JCR session.");
                return clusterData;
            }
            Node clusterNode = session.getNode(CLUSTER_PATH);
            Node intervalNode = clusterNode.hasNode(interval) ? clusterNode.getNode(interval) : null;

            if (intervalNode != null) {
                PropertyIterator properties = intervalNode.getProperties();
                while (properties.hasNext()) {
                    javax.jcr.Property property = properties.nextProperty();
                    if (property.getType() == javax.jcr.PropertyType.LONG) {
                        String userId = property.getName();
                        Long requestCount = property.getLong();
                        clusterData.put(userId, requestCount);
                        log.debug("Retrieved property for user: {} with value: {}", userId, requestCount);
                    }
                }
            } else {
                log.debug("No data found for interval: {}", interval);
            }
        } catch (LoginException | RepositoryException e) {
            log.error("Error retrieving cluster-wide data for interval {}", interval, e);
        }

        log.debug("Cluster data: {}", clusterData);
        return clusterData;
    }

    public Map<String, Map<String, Long>> getAllInstancesData(String interval) {
        log.info("Retrieving data for all instances for interval: {}", interval);
        Map<String, Map<String, Long>> instancesData = new HashMap<>();
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                log.error("Failed to obtain a JCR session.");
                return instancesData;
            }

            Node instancesNode = session.getNode(INSTANCE_PATH);
            for (Node instanceNode : JcrUtils.getChildNodes(instancesNode)) {
                String instanceName = instanceNode.getName();
                log.debug("Processing instance: {}", instanceName);

                if (instanceNode.hasNode(interval)) {
                    Node intervalNode = instanceNode.getNode(interval);
                    Map<String, Long> instanceIntervalData = new HashMap<>();

                    PropertyIterator properties = intervalNode.getProperties();
                    while (properties.hasNext()) {
                        javax.jcr.Property property = properties.nextProperty();
                        if (property.getType() == javax.jcr.PropertyType.LONG) {
                            String userId = property.getName();
                            Long requestCount = property.getLong();
                            instanceIntervalData.put(userId, requestCount);
                            log.debug("Retrieved property for user: {} with value: {}", userId, requestCount);
                        }
                    }

                    instancesData.put(instanceName, instanceIntervalData);
                } else {
                    log.debug("No data found for interval: {} in instance: {}", interval, instanceName);
                }
            }
        } catch (LoginException | RepositoryException e) {
            log.error("Error retrieving data for all instances for interval {}", interval, e);
        }

        log.debug("Instances data: {}", instancesData);
        return instancesData;
    }


/*     private Map<String, Map<String, Long>> fetchInstanceUserRequestDataAsMap() {
    
        Map<String, Map<String, Long>> aggregatedData = new TreeMap<>(new HashMap<>());
        try {
            for (String interval : ITEM_NAMES) {
                log.debug("Fetching cluster-wide data for the {}...", interval);
                Map<String, Long> userRequests = new TreeMap<>(getClusterWideData("RequestsPerUser" + interval));
                aggregatedData.put(interval, userRequests);
            }
            log.debug("Cluster-wide data retrieval successful: {} ",  aggregatedData);
            return aggregatedData;
        } catch (Exception e) {
            log.error("Error retrieving cluster-wide data", e);
            return null;
        }
    } */
}

