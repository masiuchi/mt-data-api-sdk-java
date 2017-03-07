package com.masiuchi.mtdataapi;

import junit.framework.TestCase;

public class ConstructorTest extends TestCase {
    public void testWithValidApiBaseUrl() {
        String apiBaseUrl = "http://localhost/mt/mt-data-api.cgi";
        new DataAPI(apiBaseUrl);
    }

    public void testWithNull() {
        try {
            new DataAPI(null);
        } catch (IllegalArgumentException e) {
            assertTrue(true);
            return;
        }
        assertTrue(false);
    }

    public void testWithEmptyString() {
        try {
            new DataAPI("");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
            return;
        }
        assertTrue(false);
    }
}
