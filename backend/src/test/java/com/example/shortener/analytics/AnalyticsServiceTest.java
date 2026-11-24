package com.example.shortener.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-025")
class AnalyticsServiceTest {

    @Test
    void userAgentIsStrippedOfControlCharactersAndBounded() {
        String ua = "Mozilla/5.0\r\nInjected: header" + "x".repeat(500);
        String clean = ClickSanitizer.userAgent(ua);
        assertFalse(clean.contains("\r") || clean.contains("\n"));
        assertEquals(ClickSanitizer.MAX_USER_AGENT, clean.length());
        assertNull(ClickSanitizer.userAgent("\u0000\u0001"));
        assertNull(ClickSanitizer.userAgent(null));
    }

    @Test
    void referrerIsReducedToItsOrigin() {
        assertEquals("https://ref.example", ClickSanitizer.referrer("https://ref.example/path/a?token=secret#frag"));
        assertEquals("http://ref.example:8080", ClickSanitizer.referrer("HTTP://Ref.Example:8080/x"));
    }

    @Test
    void nonHttpReferrersAreDropped() {
        assertNull(ClickSanitizer.referrer("javascript:alert(1)"));
        assertNull(ClickSanitizer.referrer("android-app://com.example"));
        assertNull(ClickSanitizer.referrer("not a url"));
        assertNull(ClickSanitizer.referrer(null));
    }

    @Tag("security")
    @Test
    void clickEventStoresNoIpAddress() {
        for (var f : ClickEvent.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse(n.contains("ip") && !n.equals("shortlinkid") || n.contains("address") || n.contains("remote"),
                    "ClickEvent must not store personal network data: " + f.getName());
        }
    }
}
