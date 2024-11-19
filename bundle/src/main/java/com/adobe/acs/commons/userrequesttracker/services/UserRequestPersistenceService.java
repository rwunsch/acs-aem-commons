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

package com.adobe.acs.commons.userrequesttracker.services;

import com.adobe.acs.commons.userrequesttracker.config.UserRequestPersistenceServiceConfig;
import com.adobe.acs.commons.userrequesttracker.core.UserRequestTrackingCore;

import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Arrays;

import javax.jcr.RepositoryException;
import javax.jcr.Node;
import javax.jcr.PropertyIterator;

@Component(service = {UserRequestPersistenceService.class, TopologyEventListener.class},
          immediate = true,
          property = {
            "service.runmode=author"
          }
    )
@Designate(ocd = UserRequestPersistenceServiceConfig.class)
public class UserRequestPersistenceService implements TopologyEventListener, Runnable {

    private static final Logger log = LoggerFactory.getLogger(UserRequestPersistenceService.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Scheduler scheduler;

    @Reference
    private UserRequestTrackingCore trackingCore;

    private int schedulerInterval;
    private boolean isEnabled;
    private String topologyId;
    private volatile boolean isClusterLeader;

    private static final String DEFAULT_PRIMARY_TYPE = "nt:unstructured";
    private static final String JOB_NAME = "user-request-persistence-service";

    @Activate
    @Modified
    protected void activate(UserRequestPersistenceServiceConfig config) {
        log.info("Activating UserRequestPersistenceService...");
        updateConfig(config);
        scheduleTask();
    }

    @Deactivate
    protected void deactivate() {
        log.info("Deactivating UserRequestPersistenceService...");
        scheduler.unschedule(JOB_NAME);  // Ensure the job is removed on deactivation
        deleteInstanceNode();
    }

    private void updateConfig(UserRequestPersistenceServiceConfig config) {
        this.schedulerInterval = config.scheduler_interval();
        this.isEnabled = config.enabled();
        log.debug("Configuration updated: schedulerInterval={} minutes, enabled={}", schedulerInterval, isEnabled);
    }

    private void scheduleTask() {
        if (isEnabled) {
            ScheduleOptions options = scheduler.EXPR("0 0/" + schedulerInterval + " * * * ?"); // Fixed interval in seconds
            options.name(JOB_NAME).canRunConcurrently(false);  // Ensure job does not run concurrently
            scheduler.schedule(this, options);
            log.info("Scheduling task with an interval of {} minutes.", schedulerInterval);
        } else {
            log.info("Persistence task is disabled.");
        }
    }

    @Override
    public void handleTopologyEvent(TopologyEvent event) {
        log.debug("Handling topology event: {}", event);
        if (event.getType() == TopologyEvent.Type.TOPOLOGY_INIT || event.getType() == TopologyEvent.Type.TOPOLOGY_CHANGED) {
            isClusterLeader = event.getNewView().getLocalInstance().isLeader();
            topologyId = event.getNewView().getLocalInstance().getSlingId();
            log.info("Cluster leadership status updated: isLeader = {}, topologyId = {}", isClusterLeader, topologyId);
        }
    }

    @Override
    public void run() {
        // Name the current thread
        Thread.currentThread().setName("UserRequestTrackingThread");
        log.info("UserRequestTracking: Running scheduled task to persist data, aggregate for cluster and RollUp to higher Timeframes. Thread renamed.");

        if (isEnabled) {
            if (isClusterLeader) {
                log.info("Instance is cluster leader. Persisting, aggregating data, and executing Rollup.");
                persistInstanceData();
                aggregateClusterData();
                updateInMemoryData();
            } else {
                log.info("Instance is not cluster leader. Persisting data, and Rollup.");
                persistInstanceData();
                updateInMemoryData();
            }
        }
    }

    protected  void persistInstanceData() {
        log.debug("Persisting instance data...");

        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);
    
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource instanceRootResource = getOrCreateResource(resourceResolver, UserRequestTrackingCore.INSTANCE_PATH, "sling:Folder");
            Resource instanceResource = getOrCreateResource(instanceRootResource, topologyId, "nt:unstructured");

            log.debug("Rolling up Instance Data for topology ID: {}", topologyId);
            rollUpData(instanceResource);

            // Collect last-minute request count data
            Map<String, Long> lastMinuteData = trackingCore.getRequestCountsLastMinute();
            Resource lastMinuteResource = getOrCreateResource(instanceResource, "RequestsPerUserLastMinute");

            Node lastMinuteNode = lastMinuteResource.adaptTo(Node.class);
            if (lastMinuteNode != null) {
                try {
                    for (Map.Entry<String, Long> entry : lastMinuteData.entrySet()) {
                        lastMinuteNode.setProperty(entry.getKey(), entry.getValue());
                    }
                    lastMinuteResource.getResourceResolver().commit(); 
                } catch (RepositoryException | PersistenceException e) {
                    log.error("Error setting properties on lastMinuteNode or committing the changes", e);
                }
            } else {
                log.error("Unable to adapt lastMinuteResource to a Node");
            }

            log.info("Instance data for RequestsPerUserLastMinute saved for topology ID: {}", topologyId);

        } catch (LoginException | PersistenceException e) {
            log.error("Error during persistence of instance data.", e);
        }
    }

    protected void aggregateClusterData() {
        log.debug("Aggregating cluster data...");

        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);
    
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource clusterResource = getOrCreateResource(resourceResolver, UserRequestTrackingCore.CLUSTER_PATH, "sling:Folder");

            // Roll up data after committing the aggregated results
            log.debug("Rolling up Cluster Data.");
            rollUpData(clusterResource);
    
            Map<String, Integer> aggregatedData = new HashMap<>();
            Resource instanceRootResource = resourceResolver.getResource(UserRequestTrackingCore.INSTANCE_PATH);
    
            if (instanceRootResource != null) {
                for (Resource instanceResource : instanceRootResource.getChildren()) {
                    Resource lastMinuteResource = instanceResource.getChild("RequestsPerUserLastMinute");
                    
                    if (lastMinuteResource != null) {
                        Node lastMinuteNode = lastMinuteResource.adaptTo(Node.class);
                        
                        if (lastMinuteNode != null) {
                            PropertyIterator propertyIterator = lastMinuteNode.getProperties();
                            while (propertyIterator.hasNext()) {
                                javax.jcr.Property property = propertyIterator.nextProperty();
                                String userName = property.getName();
    
                                // Skip system properties with colons (":")
                                if (userName.contains(":")) {
                                    log.debug("Skipping system property '{}' with a colon in the key", userName);
                                    continue;
                                }
    
                                // Ensure the property is an integer before aggregation
                                if (property.getType() == javax.jcr.PropertyType.LONG) {
                                    int value = (int) property.getLong();
                                    aggregatedData.merge(userName, value, Integer::sum);
                                } else {
                                    log.debug("Skipping non-integer property '{}' with type '{}'", userName, property.getType());
                                }
                            }
                        } else {
                            log.error("Unable to adapt lastMinuteResource to a Node for reading properties when aggregating ClusterData.");
                        }
                    }
                }
            }
    
            // Now write the aggregated data back to clusterResource
            if (clusterResource != null) {
                for (Map.Entry<String, Integer> entry : aggregatedData.entrySet()) {
                    Node clusterIntervalNode = getOrCreateResource(clusterResource, "RequestsPerUserLastMinute").adaptTo(Node.class);   
                    clusterIntervalNode.setProperty(entry.getKey(), entry.getValue());
                }
                resourceResolver.commit(); // Commit the aggregated results to the cluster node
    
            } else {
                log.error("ClusterResource (Node) is null, when trying to write data to the Sub-Interval-Node .");
            }
        } catch (LoginException | RepositoryException | PersistenceException e) {
            log.error("Error during cluster data aggregation", e);
        }

    }
    

    private void rollUpData(Resource parentResource) {
        LocalDateTime now = LocalDateTime.now();
        log.info("Rolling up data for node '{}'. LocalTime for Rollup: Minute: {}, Hour: {}, Day: {}, Week: {}, Month: {}, Year: {}", parentResource.getName() , now.getMinute(), now.getHour(), now.getDayOfMonth(), now.getDayOfWeek(), now.getMonth(), now.getYear());
        
        rollUpSubResource(parentResource, "RequestsPerUserLastMinute", "RequestsPerUserLastHour", "LastHour");

        if (now.getHour() == 0 && now.getMinute() == 0) {
            rollUpSubResource(parentResource, "RequestsPerUserLastHour", "RequestsPerUserLastDay", "LastDay");
        }
        
        if (now.getDayOfWeek().getValue() == 1 && now.getHour() == 0 && now.getMinute() == 0) {
            rollUpSubResource(parentResource, "RequestsPerUserLastDay", "RequestsPerUserLastWeek", "LastWeek");
        }
        if (now.getDayOfMonth() == 1 && now.getHour() == 0 && now.getMinute() == 0) {
            rollUpSubResource(parentResource, "RequestsPerUserLastWeek", "RequestsPerUserLastMonth", "LastMonth");
        }
        if (now.getDayOfYear() == 1 && now.getHour() == 0 && now.getMinute() == 0) {
            rollUpSubResource(parentResource, "RequestsPerUserLastMonth", "RequestsPerUserLastYear", "LastYear");
            Node instanceOrClusterNode = parentResource.adaptTo(Node.class);
            if (instanceOrClusterNode != null) {
                try {
                    instanceOrClusterNode.setProperty("dataYear", now.getYear() - 1); // Set to previous year
                    parentResource.getResourceResolver().commit();
                } catch (RepositoryException | PersistenceException e) {
                    log.error("Error setting '' property on instance or cluster node", e);
                }
            } else {
                log.error("Unable to adapt parentResource to a Node for setting RequestsPerUserForever property.");
            }               

        }
        if (now.getDayOfYear() == 1 && now.getHour() == 0 && now.getMinute() == 0) {
            // Roll-up data older than the current year into RequestsPerUserForever
            try {
                Node instanceOrClusterNode = parentResource.adaptTo(Node.class);
                if (instanceOrClusterNode != null && instanceOrClusterNode.hasProperty("dataYear")) {
                    int dataYear = (int) instanceOrClusterNode.getProperty("dataYear").getLong();
                    if (dataYear < now.getYear()) {
                        rollUpSubResource(parentResource, "RequestsPerUserLastYear", "RequestsPerUserForever" , "Forever");
                        log.info("Moved data from RequestsPerUserLastYear (Year: {}) to RequestsPerUserForever", dataYear);
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to roll up data from RequestsPerUserLastYear to RequestsPerUserForever", e);
            }
        }
    }

    private void rollUpSubResource(Resource parentResource, String fromNodeName, String toNodeName, String interval) {
        log.debug("Rolling up data from {} to {}", fromNodeName, toNodeName);
        try {
            // Adapt parentResource to a Node for centralized timestamp management
            Node instanceOrClusterNode = parentResource.adaptTo(Node.class);
            if (instanceOrClusterNode == null) {
                log.error("Unable to adapt parent resource '{}' to Node for managing rollup timestamps.", parentResource.getPath());
                return;
            }
    
            // Check and initialize lastRollupTimestamp_<interval> if not present
            String timestampProperty = "lastRollupTimestamp_" + interval;
            long currentTime = System.currentTimeMillis();
            long lastRollupTime = 0;
            if (instanceOrClusterNode.hasProperty(timestampProperty)) {
                lastRollupTime = instanceOrClusterNode.getProperty(timestampProperty).getLong();
            } else {
                // Initialize the timestamp for the interval
                log.info("Initializing {} for parent resource '{}'", timestampProperty, parentResource.getPath());
                instanceOrClusterNode.setProperty(timestampProperty, 0L);
                lastRollupTime = 0L;
            }
    
            // Skip rollup if it has already occurred in the current interval
            long intervalBoundary = getIntervalBoundary(interval);
            if (lastRollupTime >= intervalBoundary) {
                log.debug("Rollup already performed for interval '{}' from '{}' to '{}'.", intervalBoundary, fromNodeName, toNodeName);
                return;
            }
    
            // Access the 'from' and 'to' nodes for rollup
            Resource fromResource = getOrCreateResource(parentResource, fromNodeName, "nt:unstructured");
            Resource toResource = getOrCreateResource(parentResource, toNodeName, "nt:unstructured");
    
            if (fromResource == null || toResource == null) {
                log.error("Source or target resource is null. Skipping rollup from '{}' to '{}'.", fromNodeName, toNodeName);
                return;
            }
    
            Node fromNode = fromResource.adaptTo(Node.class);
            Node toNode = toResource.adaptTo(Node.class);
    
            if (fromNode == null || toNode == null) {
                log.error("Unable to adapt resources '{}' or '{}' to JCR nodes. Skipping rollup.", fromNodeName, toNodeName);
                return;
            }
    
            // Perform the rollup from 'fromNode' to 'toNode'
            PropertyIterator propertyIterator = fromNode.getProperties();
            while (propertyIterator.hasNext()) {
                javax.jcr.Property property = propertyIterator.nextProperty();
                String userName = property.getName();
    
                if (userName.contains(":")) {
                    log.debug("Skipping system property '{}'", userName);
                    continue;
                }
    
                if (property.getType() == javax.jcr.PropertyType.LONG) {
                    int existingValue = toNode.hasProperty(userName) ? 
                            (int) toNode.getProperty(userName).getLong() : 0;
                    int rolledUpValue = (int) property.getLong() + existingValue;
    
                    toNode.setProperty(userName, rolledUpValue);
                    property.remove();
                } else {
                    log.debug("Skipping non-integer property '{}'", userName);
                }
            }
    
            // Update the lastRollupTimestamp_<interval>
            instanceOrClusterNode.setProperty(timestampProperty, currentTime);
            parentResource.getResourceResolver().commit();
    
            log.info("Rollup completed from '{}' to '{}'. {} updated to '{}'.",
                    fromNodeName, toNodeName, timestampProperty, currentTime);
        } catch (Exception e) {
            log.error("Error during roll-up operation from '{}' to '{}'.", fromNodeName, toNodeName, e);
        }
    }
      
    
    // For when we need an overloead with resourceResolver and path
/*     private Resource getOrCreateResource(ResourceResolver resolver, String path) throws PersistenceException {
        return getOrCreateResource(resolver, path, DEFAULT_PRIMARY_TYPE);
    } */
    
    private Resource getOrCreateResource(Resource parentResource, String relativePath) throws PersistenceException {
        return getOrCreateResource(parentResource, relativePath, DEFAULT_PRIMARY_TYPE);
    }
    
    private Resource getOrCreateResource(ResourceResolver resolver, String path, String primaryType) throws PersistenceException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (primaryType == null || primaryType.isEmpty()) {
            throw new IllegalArgumentException("Primary type cannot be null or empty");
        }
    
        // ResourceResolver is expected to be managed externally (caller should close it)
        Resource currentResource = resolver.getResource("/");
        return getOrCreateResource(currentResource, path, primaryType);
    }
    
    private Resource getOrCreateResource(Resource parentResource, String relativePath, String primaryType) throws PersistenceException {
        if (parentResource == null) {
            throw new IllegalArgumentException("Parent resource cannot be null");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("Relative path cannot be null or empty");
        }
        if (primaryType == null || primaryType.isEmpty()) {
            throw new IllegalArgumentException("Primary type cannot be null or empty");
        }
    
        // Split the relative path and iterate through each segment
        String[] pathSegments = Arrays.stream(relativePath.split("/"))
                                      .filter(segment -> !segment.isEmpty())
                                      .toArray(String[]::new);
        Resource currentResource = parentResource;
    
        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];
            Resource nextResource = currentResource.getChild(segment);
    
            // If the next resource does not exist, create it
            if (nextResource == null) {
                Map<String, Object> properties = new HashMap<>();
                boolean isFinalSegment = (i == pathSegments.length - 1);
                properties.put("jcr:primaryType", isFinalSegment ? primaryType : "sling:Folder");
    
                nextResource = parentResource.getResourceResolver().create(currentResource, segment, properties);
                log.info("Created resource at path: {}", nextResource.getPath());
                currentResource.getResourceResolver().commit(); 
            }
            currentResource = nextResource;
        }
    
        parentResource.getResourceResolver().commit(); // Save all changes
        return currentResource;
    }    
    

    protected void deleteInstanceNode() {
        log.debug("Deleting instance node...");

        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);
    
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource instanceResource = resourceResolver.getResource(UserRequestTrackingCore.INSTANCE_PATH + "/" + topologyId);
            if (instanceResource != null) {
                resourceResolver.delete(instanceResource);
                resourceResolver.commit();
                log.info("Deleted instance node for topology ID: {}", topologyId);
            }
        } catch (LoginException | PersistenceException e) {
            log.error("Error deleting instance node on deactivation", e);
        }
    }

    public void updateInMemoryData() {
        log.debug("Updating in-memory data...");
        trackingCore.setRequestCountsLastMinute(getInstanceData("RequestsPerUserLastMinute"));
        trackingCore.clearRequestCountsLastMinute();
        trackingCore.setRequestCountsLastHour(getInstanceData("RequestsPerUserLastHour"));
        trackingCore.setRequestCountsLastDay(getInstanceData("RequestsPerUserLastDay"));
        trackingCore.setRequestCountsLastWeek(getInstanceData("RequestsPerUserLastWeek"));
        trackingCore.setRequestCountsLastMonth(getInstanceData("RequestsPerUserLastMonth"));
        trackingCore.setRequestCountsLastYear(getInstanceData("RequestsPerUserLastYear"));
        trackingCore.setRequestCountsForever(getInstanceData("RequestsPerUserForever"));
    }

    public Map<String, Long> getInstanceData(String interval) {
        log.debug("Retrieving instance data for interval: {}", interval);
        Map<String, Long> instanceData = new HashMap<>();

        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);
    
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource intervalResource = getOrCreateResource(resourceResolver, UserRequestTrackingCore.INSTANCE_PATH + "/" + topologyId + "/" + interval, "nt:unstructured");
    
            Node intervalNode = intervalResource.adaptTo(Node.class);
            if (intervalNode != null) {
                PropertyIterator propertyIterator = intervalNode.getProperties();
                while (propertyIterator.hasNext()) {
                    javax.jcr.Property property = propertyIterator.nextProperty();
                    String propertyName = property.getName();
    
                    // Skip system properties (those containing ":")
                    if (propertyName.contains(":")) {
                        log.debug("Skipping system property '{}' (property with a colon in the key)", propertyName);
                        continue;
                    }
    
                    // Only process properties of type LONG or INTEGER
                    if (property.getType() == javax.jcr.PropertyType.LONG || property.getType() == javax.jcr.PropertyType.DOUBLE) {
                        instanceData.put(propertyName, property.getLong());
                    } else {
                        log.debug("Skipping non-integer property '{}' with type '{}'", propertyName, property.getType());
                    }
                }
            } else {
                log.error("Unable to adapt intervalResource to a Node for reading instance data.");
            }
        } catch (LoginException | RepositoryException | PersistenceException e) {
            log.error("Error retrieving instance-level data from repository for interval: {}", interval, e);
        }
        return instanceData;
    }

    private long getIntervalBoundary(String intervalName) {
        LocalDateTime now = LocalDateTime.now();
        switch (intervalName) {
            case "RequestsPerUserLastHour":
                return now.withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli();
            case "RequestsPerUserLastDay":
                return now.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli();
            case "RequestsPerUserLastWeek":
                return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli();
            case "RequestsPerUserLastMonth":
                return now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli();
            case "RequestsPerUserLastYear":
                return now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli();
            default:
                return System.currentTimeMillis();
        }
    }
    
}
