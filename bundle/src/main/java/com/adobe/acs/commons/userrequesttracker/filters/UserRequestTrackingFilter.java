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

package com.adobe.acs.commons.userrequesttracker.filters;

import com.adobe.acs.commons.userrequesttracker.core.UserRequestTrackingCore;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = Filter.class,
    immediate = true,
    property = {
        "sling.filter.scope=request",
        "service.ranking:Integer=-1000",
        "service.runmode=author"
    }
)
public class UserRequestTrackingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UserRequestTrackingFilter.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private volatile UserRequestTrackingCore trackingCore;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.debug("Initializing UserRequestTrackingFilter...");
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response,
                         final FilterChain filterChain) throws IOException, ServletException {

        final SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
        //log.debug("Processing request in UserRequestTrackingFilter for URI: {}", slingRequest.getRequestURI());

        String userId = slingRequest.getRemoteUser();
        if (userId != null && !"system".equals(userId) && !"anonymous".equals(userId)) {
            log.debug("Tracking request for user ID: {}", userId);
            trackingCore.mergeRequestCountsLastMinute(userId, 1L, Long::sum);
            log.debug("Updated request count for user '{}' to {} in last minute data.", userId, trackingCore.getRequestCountsLastMinute().get(userId));
        } else {
            log.debug("Request received from non-trackable user (userId: {}). Skipping.", userId);
        }

        filterChain.doFilter(request, response);
        //log.debug("Request processing completed in UserRequestTrackingFilter.Tracked request in UserRequestTrackingMBean for URI: {}", slingRequest.getRequestURI());
    }

    @Override
    public void destroy() {
        log.debug("Destroying UserRequestTrackingFilter...");
    }
}
