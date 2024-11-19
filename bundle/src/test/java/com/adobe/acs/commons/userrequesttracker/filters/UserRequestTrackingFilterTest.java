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
import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/* 
Initialization: Using Mockito to create mock objects for dependencies (UserRequestTrackingCore, SlingHttpServletRequest, and FilterChain).
Trackable User: Testing the scenario where the user is valid and should be tracked.
Non-Trackable Users: Testing cases for "system," "anonymous," and null users to ensure they are skipped.
Request Count Update: Verifying that the mergeRequestCountsLastMinute method is called with the correct parameters for valid users. 
*/

class UserRequestTrackingFilterTest {

    private UserRequestTrackingFilter filter;

    @Mock
    private UserRequestTrackingCore trackingCore;

    @Mock
    private SlingHttpServletRequest slingRequest;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        filter = new UserRequestTrackingFilter();
        // Inject the mock trackingCore into the filter using reflection
        java.lang.reflect.Field trackingCoreField = UserRequestTrackingFilter.class.getDeclaredField("trackingCore");
        trackingCoreField.setAccessible(true);
        trackingCoreField.set(filter, trackingCore);
    }

    @Test
    void testDoFilter_withTrackableUser() throws ServletException, IOException {
        // Arrange
        String testUserId = "testUser";
        when(slingRequest.getRemoteUser()).thenReturn(testUserId);
        when(trackingCore.getRequestCountsLastMinute()).thenReturn(new ConcurrentHashMap<>());

        // Act
        filter.doFilter(slingRequest, mock(ServletResponse.class), filterChain);

        // Assert
        verify(trackingCore).mergeRequestCountsLastMinute(eq(testUserId), eq(1L), any());
        verify(filterChain).doFilter(eq(slingRequest), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_withNonTrackableUser() throws ServletException, IOException {
        // Arrange
        when(slingRequest.getRemoteUser()).thenReturn("system");

        // Act
        filter.doFilter(slingRequest, mock(ServletResponse.class), filterChain);

        // Assert
        verify(trackingCore, never()).mergeRequestCountsLastMinute(anyString(), anyLong(), any());
        verify(filterChain).doFilter(eq(slingRequest), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_withAnonymousUser() throws ServletException, IOException {
        // Arrange
        when(slingRequest.getRemoteUser()).thenReturn("anonymous");

        // Act
        filter.doFilter(slingRequest, mock(ServletResponse.class), filterChain);

        // Assert
        verify(trackingCore, never()).mergeRequestCountsLastMinute(anyString(), anyLong(), any());
        verify(filterChain).doFilter(eq(slingRequest), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_withNullUser() throws ServletException, IOException {
        // Arrange
        when(slingRequest.getRemoteUser()).thenReturn(null);

        // Act
        filter.doFilter(slingRequest, mock(ServletResponse.class), filterChain);

        // Assert
        verify(trackingCore, never()).mergeRequestCountsLastMinute(anyString(), anyLong(), any());
        verify(filterChain).doFilter(eq(slingRequest), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_updatesRequestCount() throws ServletException, IOException {
        // Arrange
        String testUserId = "testUser";
        when(slingRequest.getRemoteUser()).thenReturn(testUserId);
        ConcurrentHashMap<String, Long> mockData = new ConcurrentHashMap<>();
        when(trackingCore.getRequestCountsLastMinute()).thenReturn(mockData);

        // Act
        filter.doFilter(slingRequest, mock(ServletResponse.class), filterChain);

        // Assert
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> countCaptor = ArgumentCaptor.forClass(Long.class);
        verify(trackingCore).mergeRequestCountsLastMinute(userIdCaptor.capture(), countCaptor.capture(), any());
        assertEquals(testUserId, userIdCaptor.getValue());
        assertEquals(1, countCaptor.getValue());
    }
}
