package com.example.shortener.analytics;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.shortener.ShortLinkRepository;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-025")
@Requirement("001-FR-027")
class ClickFlowIT {
    @Autowired MockMvc mvc;
    @Autowired ClickEventRepository clicks;
    @Autowired ShortLinkRepository links;

    private void create(String alias) throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/" + alias + "\",\"alias\":\"" + alias + "\"}")).andExpect(status().isCreated());
    }

    @Test
    void createRedirectClickAnalyticsFlowShowsClickWithinFiveSeconds() throws Exception {
        create("click-flow");
        Instant t0 = Instant.now();
        mvc.perform(get("/click-flow").header(HttpHeaders.REFERER, "https://ref.example/page?secret=1")
                .header(HttpHeaders.USER_AGENT, "TestAgent/1.0")).andExpect(status().isFound());
        mvc.perform(get("/click-flow")).andExpect(status().isFound());

        mvc.perform(get("/api/analytics/links/click-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.recentEvents.items", hasSize(2)))
                .andExpect(jsonPath("$.clicksOverTime", not(empty())));
        assertTrue(Duration.between(t0, Instant.now()).toSeconds() < 5, "click must be visible within 5 seconds");

        var stored = clicks.findAll().stream().filter(c -> "https://ref.example".equals(c.getReferrer())).toList();
        assertEquals(1, stored.size(), "referrer is reduced to its origin");
        assertEquals("TestAgent/1.0", stored.get(0).getUserAgent());
    }

    @Test
    void summaryHasTotalsTopLinksAndPaginatedRecentEvents() throws Exception {
        create("top-a");
        create("top-b");
        for (int i = 0; i < 3; i++) mvc.perform(get("/top-a")).andExpect(status().isFound());
        mvc.perform(get("/top-b")).andExpect(status().isFound());

        mvc.perform(get("/api/analytics/summary").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalLinks", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.topLinks[?(@.code=='top-a')].clicks", hasItem(3)))
                .andExpect(jsonPath("$.recentEvents.items", hasSize(2)))
                .andExpect(jsonPath("$.recentEvents.size").value(2))
                .andExpect(jsonPath("$.clicksOverTime.length()").value(greaterThanOrEqualTo(30)));
    }

    @Test
    void linkListShowsClickCounts() throws Exception {
        create("count-me");
        mvc.perform(get("/count-me"));
        mvc.perform(get("/count-me"));
        mvc.perform(get("/api/links/count-me")).andExpect(jsonPath("$.clicks").value(2));
        mvc.perform(get("/api/links").param("size", "100"))
                .andExpect(jsonPath("$.items[?(@.code=='count-me')].clicks", hasItem(2)));
    }

    @Test
    void unknownLinkAnalyticsReturns404AndInactiveLinksRecordNoClicks() throws Exception {
        mvc.perform(get("/api/analytics/links/nope-nope")).andExpect(status().isNotFound());
        create("inactive-click");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/links/inactive-click"));
        mvc.perform(get("/inactive-click")).andExpect(status().isNotFound());
        mvc.perform(get("/api/analytics/links/inactive-click")).andExpect(jsonPath("$.totalClicks").value(0));
    }
}
