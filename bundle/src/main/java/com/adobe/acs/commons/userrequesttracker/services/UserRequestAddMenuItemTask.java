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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.framework.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.acs.commons.userrequesttracker.core.UserRequestTrackingCore;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

@Component(service = {Runnable.class, BundleListener.class}, immediate = true)
public class UserRequestAddMenuItemTask implements Runnable, SynchronousBundleListener {

    private static final Logger log = LoggerFactory.getLogger(UserRequestAddMenuItemTask.class);

    private static final String TOOLS_PATH = "/apps/cq/core/content/nav/tools";
    private static final String SUBMENU_NAME = "acs-commons";
    private static final String SUBMENU_TITLE = "ACS AEM Commons";
    private static final String CUSTOM_TOOL_ID = "user-request-tracker";
    private static final String CUSTOM_TOOL_NAME = "user-request-tracker";
    private static final String CUSTOM_TOOL_TITLE = "User Request Tracker";
    private static final String CUSTOM_TOOL_DESCRIPTION = "A tool to view user requests per time period.";
    private static final String CUSTOM_TOOL_URL = "/apps/userrequesttracker/content/user-request-tracker.html";
    private static final String CUSTOM_TOOL_ICON = "coral-Icon--document";
    private static final String ACS_COMMONS_BUNDLE_NAME = "com.adobe.acs.acs-aem-commons-bundle";
    //private static final String SLING_RESOURCE_TYPE = "cq/gui/components/authoring/tools/tool";
    

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private BundleContext bundleContext;

    @Activate
    protected void activate(BundleContext context) {
        log.info("AddMenuItemTask activated. Dependencies resolved.");
        this.bundleContext = context;

        // Register this class as a bundle listener
        bundleContext.addBundleListener(this);

        // Check if ACS Commons is already active
        boolean acsCommonsActive = false;
        for (Bundle bundle : bundleContext.getBundles()) {
            if (isAcsCommonsBundleActive(bundle)) {
                acsCommonsActive = true;
                break;
            }
        }

        if (acsCommonsActive) {
            log.info("ACS Commons bundle is already active. Running menu creation task...");
            run();
        } else {
            log.info("ACS Commons bundle is not active. Creating ACS Commons Menu ...");
            run();
        }
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        Bundle bundle = event.getBundle();

        if (isAcsCommonsBundleActive(bundle)) {
            log.info("ACS Commons bundle became active. Running menu creation task...");
            run();
        }
    }

    private boolean isAcsCommonsBundleActive(Bundle bundle) {
        return ACS_COMMONS_BUNDLE_NAME.equals(bundle.getSymbolicName()) && bundle.getState() == Bundle.ACTIVE;
    }

    @Override
    public void run() {
        log.info("Starting AddMenuItemTask...");
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, UserRequestTrackingCore.SERVICE_USER);

        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            addCustomSubMenu(resourceResolver);
            log.info("AddMenuItemTask completed successfully.");
        } catch (Exception e) {
            log.error("Failed to add submenu: {}", e.getMessage(), e);
        }
    }

    private void addCustomSubMenu(ResourceResolver resourceResolver) throws RepositoryException, PersistenceException {
        Resource toolsResource = resourceResolver.getResource(TOOLS_PATH);

        if (toolsResource == null) {
            log.error("The path {} does not exist.", TOOLS_PATH);
            throw new IllegalStateException("Tools path does not exist: " + TOOLS_PATH);
        }

        Node toolsNode = toolsResource.adaptTo(Node.class);
        if (toolsNode == null) {
            log.error("Failed to adapt resource {} to a JCR Node.", TOOLS_PATH);
            throw new IllegalStateException("Failed to adapt tools resource to a JCR Node.");
        }

        // Create the submenu node if it doesn't exist
        Node submenuNode;
        if (toolsNode.hasNode(SUBMENU_NAME)) {
            submenuNode = toolsNode.getNode(SUBMENU_NAME);
            log.info("Submenu '{}' already exists.", SUBMENU_NAME);
        } else {
            log.info("Creating submenu '{}'...", SUBMENU_NAME);
            submenuNode = toolsNode.addNode(SUBMENU_NAME, JcrResourceConstants.NT_SLING_ORDERED_FOLDER);
            submenuNode.setProperty("jcr:title", SUBMENU_TITLE);
            submenuNode.setProperty("sling:resourceType", "granite/ui/components/foundation/container");
        }

        // Add the custom tool to the submenu
        if (submenuNode.hasNode(CUSTOM_TOOL_NAME)) {
            log.info("Custom tool '{}' already exists in submenu '{}'.", CUSTOM_TOOL_NAME, SUBMENU_NAME);
        } else {
            log.info("Adding custom tool '{}' to submenu '{}'...", CUSTOM_TOOL_NAME, SUBMENU_NAME);
            Node customToolNode = submenuNode.addNode(CUSTOM_TOOL_NAME, "nt:unstructured");
            customToolNode.setProperty("id", CUSTOM_TOOL_ID);
            customToolNode.setProperty("jcr:title", CUSTOM_TOOL_TITLE);
            customToolNode.setProperty("jcr:description", CUSTOM_TOOL_DESCRIPTION);
            customToolNode.setProperty("cq:icon", CUSTOM_TOOL_ICON);
            customToolNode.setProperty("href", CUSTOM_TOOL_URL);
            customToolNode.setProperty("target", "_blank");
            //customToolNode.setProperty("sling:resourceType", "SLING_RESOURCE_TYPE");
            log.info("Custom tool '{}' added to submenu '{}'.", CUSTOM_TOOL_NAME, SUBMENU_NAME);
        }

        // Save changes
        resourceResolver.adaptTo(javax.jcr.Session.class).save();
    }
}
