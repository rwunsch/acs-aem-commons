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

package com.adobe.acs.commons.userrequesttracker.servlets;

import com.adobe.acs.commons.userrequesttracker.core.UserRequestTrackingCore;
import com.adobe.acs.commons.userrequesttracker.mbeans.UserRequestTrackingMBean;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/* @Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/userrequesttracker",
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.extensions=json",
        "service.runmode=author"
    }
) */
// resourceTypes="sling/servlet/default"

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes="acs-commons/components/utilities/user-request-tracker",
        methods=HttpConstants.METHOD_GET,
        selectors = {"request-api"},
        extensions={"json"})
@ServiceDescription("User Request Data Servlet")

public class UserRequestDataServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(UserRequestDataServlet.class);


    @Reference
    private transient UserRequestTrackingMBean userRequestTrackingMBean;

    @Reference
    private transient ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

        log.warn("UserRequestDataServlet activated with extension {} and selector {}", request.getRequestPathInfo().getExtension(), request.getRequestPathInfo().getSelectorString());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
    
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);
    
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            UserManager userManager = resourceResolver.adaptTo(UserManager.class);
            if (userManager == null) {
                throw new RepositoryException("UserManager could not be adapted from ResourceResolver");
            }
    
            //JsonObject jsonResponse = new JsonObject();
            Map<String, Object> jsonResponse = new HashMap<>();
    
            // Fetch cluster-wide data
            log.info("Fetching cluster-wide user request data");
            Map<String, Map<String, Long>> clusterData = userRequestTrackingMBean.fetchClusterWideUserRequestDataAsMap();
            JsonObject clusterObject = new JsonObject();
    
            for (Map.Entry<String, Map<String, Long>> entry : clusterData.entrySet()) {
                String timeInterval = entry.getKey();
                Map<String, Long> userRequests = entry.getValue();
    
                JsonObject intervalObject = new JsonObject();
                for (Map.Entry<String, Long> userEntry : userRequests.entrySet()) {
                    String userId = userEntry.getKey();
                    Long requestCount = userEntry.getValue();
    
                    JsonObject userObject = getUserInfo(userManager, userId, requestCount);
                    if (userObject != null) {
                        intervalObject.add(userId, userObject);
                    }
                }
                clusterObject.add(timeInterval, intervalObject);
            }
    
            //jsonResponse.add("Cluster", clusterObject);
            jsonResponse.put("Cluster", clusterObject);
    
            // Fetch instance-specific data
            log.info("Fetching instance-specific user request data");
            Map<String, Map<String, Map<String, Long>>> instanceData = userRequestTrackingMBean.fetchAllInstancesDataForAllIntervalsAsMap();
    
            for (Map.Entry<String, Map<String, Map<String, Long>>> instanceEntry : instanceData.entrySet()) {
                String instanceId = instanceEntry.getKey();
                Map<String, Map<String, Long>> instanceIntervals = instanceEntry.getValue();
    
                JsonObject instanceObject = new JsonObject();
                for (Map.Entry<String, Map<String, Long>> intervalEntry : instanceIntervals.entrySet()) {
                    String timeInterval = intervalEntry.getKey();
                    Map<String, Long> userRequests = intervalEntry.getValue();
    
                    JsonObject intervalObject = new JsonObject();
                    for (Map.Entry<String, Long> userEntry : userRequests.entrySet()) {
                        String userId = userEntry.getKey();
                        Long requestCount = userEntry.getValue();
    
                        JsonObject userObject = getUserInfo(userManager, userId, requestCount);
                        if (userObject != null) {
                            intervalObject.add(userId, userObject);
                        }
                    }
                    instanceObject.add(timeInterval, intervalObject);
                }
    
                //jsonResponse.add(instanceId, instanceObject);
                jsonResponse.put(instanceId, instanceObject);
            }

            // Use Gson to serialize the JsonObject into a valid JSON string
            Gson gson = new Gson();
            String jsonOutput = gson.toJson(jsonResponse);
    
            response.getWriter().write(jsonOutput);
            log.info("User request data successfully fetched and written to response");
    
        } catch (Exception e) {
            log.error("Error fetching user request data", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal Server Error\"}");
        }
    }
    
    private JsonObject getUserInfo(UserManager userManager, String userId, Long requestCount) {
        try {
            Authorizable authorizable = userManager.getAuthorizable(userId);
            if (authorizable != null && !authorizable.isGroup()) {
                User user = (User) authorizable;
                JsonObject userObject = new JsonObject();
    
                userObject.addProperty("UserID", userId);
                userObject.addProperty("firstName", getUserProperty(user, "profile/givenName"));
                userObject.addProperty("lastName", getUserProperty(user, "profile/familyName"));
                userObject.addProperty("eMail", getUserProperty(user, "profile/email"));
                userObject.addProperty("requestCount", requestCount);
    
                return userObject;
            } else {
                log.warn("User not found or is a group: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error fetching user info for user: {}", userId, e);
        }
        return null;
    }
    
    private String getUserProperty(User user, String propertyPath) {
        try {
            if (user.hasProperty(propertyPath)) {
                return user.getProperty(propertyPath)[0].getString();
            }
        } catch (RepositoryException e) {
            log.debug("Error fetching property {} of User. ", propertyPath, e);
        }
        return "";
    }
    
}