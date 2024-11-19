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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


/* 
testGetRequestsPerUserLastMinute and Similar Methods:
* Mock the data returned by trackingCore.
* Assert that the CompositeData contains the correct values.

testFetchClusterWideUserRequestData:
* Mock getClusterWideData for different intervals.
* Verify that the TabularData structure is correctly populated.

testFetchClusterWideUserRequestDataAsMap:
* Ensure the map has correct intervals and associated data.

testResetRequestCounts:
* Verify that resetRequestCounts is called exactly once and does not throw exceptions. */

class UserRequestTrackingMBeanTest {

    @Mock
    private UserRequestTrackingCore trackingCore;

    @InjectMocks
    private UserRequestTracking userRequestTracking;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetRequestsPerUserLastMinute() {
        Map<String, Long> mockData = new HashMap<>();
        mockData.put("user1", 10L);
        mockData.put("user2", 20L);
        when(trackingCore.getRequestCountsLastMinute()).thenReturn(mockData);

        CompositeData result = userRequestTracking.getRequestsPerUserLastMinute();

        assertNotNull(result);
        assertEquals(10L, result.get("user1"));
        assertEquals(20L, result.get("user2"));
    }

    @Test
    void testGetRequestsPerUserLastHour() {
        Map<String, Long> mockData = new HashMap<>();
        mockData.put("user1", 5L);
        mockData.put("user2", 15L);
        when(trackingCore.getRequestCountsLastHour()).thenReturn(mockData);

        CompositeData result = userRequestTracking.getRequestsPerUserLastHour();

        assertNotNull(result);
        assertEquals(5L, result.get("user1"));
        assertEquals(15L, result.get("user2"));
    }

/*     @Test
    void testFetchClusterWideUserRequestData() {
        Map<String, Long> mockData = Map.of("user1", 100L, "user2", 200L);
        when(trackingCore.getClusterWideData(anyString())).thenReturn(mockData);
    
        TabularData result = userRequestTracking.fetchClusterWideUserRequestData();
    
        assertNotNull(result);
        assertEquals(7, result.size()); // Assuming 7 intervals and 2 users per interval
    
        // Additional assertions to verify the content of the TabularData
        for (String interval : UserRequestTrackingCore.ITEM_NAMES) {
            String intervalKey = "1 " + interval;
            assertTrue(result.containsKey(new Object[]{intervalKey}));
            CompositeData intervalData = result.get(new Object[]{intervalKey});
            assertNotNull(intervalData);
            assertEquals(100L, intervalData.get("user1"));
            assertEquals(200L, intervalData.get("user2"));
        }
    } */

/*     @Test
    void testFetchClusterWideUserRequestDataAsMap() {
        Map<String, Long> intervalData1 = Map.of("admin", 31L);
        Map<String, Long> intervalData2 = Map.of("admin", 2043L, "wunsch", 1746L);
        Map<String, Long> intervalData3 = Map.of();
        Map<String, Long> intervalData4 = Map.of();
        Map<String, Long> intervalData5 = Map.of();
        Map<String, Long> intervalData6 = Map.of();
        Map<String, Long> intervalData7 = Map.of("admin", 100L, "user2", 200L);
    
        when(trackingCore.getClusterWideData("1 LastMinute")).thenReturn(intervalData1);
        when(trackingCore.getClusterWideData("2 LastHour")).thenReturn(intervalData2);
        when(trackingCore.getClusterWideData("3 LastDay")).thenReturn(intervalData3);
        when(trackingCore.getClusterWideData("4 LastWeek")).thenReturn(intervalData4);
        when(trackingCore.getClusterWideData("5 LastMonth")).thenReturn(intervalData5);
        when(trackingCore.getClusterWideData("6 LastYear")).thenReturn(intervalData6);
        when(trackingCore.getClusterWideData("7 Forever")).thenReturn(intervalData7);
    
        Map<String, Map<String, Long>> result = userRequestTracking.fetchClusterWideUserRequestDataAsMap();
    
        assertNotNull(result);
        assertEquals(7, result.size()); // Assuming 7 intervals
        assertTrue(result.containsKey("1 LastMinute"));
        assertTrue(result.containsKey("2 LastHour"));
        assertTrue(result.containsKey("3 LastDay"));
        assertTrue(result.containsKey("4 LastWeek"));
        assertTrue(result.containsKey("5 LastMonth"));
        assertTrue(result.containsKey("6 LastYear"));
        assertTrue(result.containsKey("7 Forever"));
    
        assertEquals(31L, result.get("1 LastMinute").get("admin"));
        assertEquals(2043L, result.get("2 LastHour").get("admin"));
        assertEquals(1746L, result.get("2 LastHour").get("wunsch"));
        assertTrue(result.get("3 LastDay").isEmpty());
        assertTrue(result.get("4 LastWeek").isEmpty());
        assertTrue(result.get("5 LastMonth").isEmpty());
        assertTrue(result.get("6 LastYear").isEmpty());
        assertEquals(100L, result.get("7 Forever").get("admin"));
        assertEquals(200L, result.get("7 Forever").get("user2"));
    } */

    @Test
    void testResetRequestCounts() {
        doNothing().when(trackingCore).resetRequestCounts();

        assertDoesNotThrow(() -> userRequestTracking.resetRequestCounts());
        verify(trackingCore, times(1)).resetRequestCounts();
    }
}
