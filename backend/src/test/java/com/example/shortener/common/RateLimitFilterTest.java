package com.example.shortener.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "shortener.rate-limit.create-per-minute=3",
        "shortener.rate-limit.analytics-per-minute=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("security")
@Requirement("001-FR-028")
class RateLimitFilterTest {
    @Autowired MockMvc mvc;

    private void create(int expectedStatusIsCreated, String forwardedFor) throws Exception {
        var req = post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/rl\"}");
        if (forwardedFor != null) req = req.header("X-Forwarded-For", forwardedFor);
        mvc.perform(req).andExpect(status().is(expectedStatusIsCreated));
    }

    @Test
    void creationIsLimitedWith429AndStandardErrorBody() throws Exception {
        for (int i = 0; i < 3; i++) create(201, null);
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/rl\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        // Exhaust (the bucket may already be partly used by the previous test in this context).
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/rl\"}").header("X-Forwarded-For", "10.0.0." + i));
        }
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/rl\"}").header("X-Forwarded-For", "203.0.113.77"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void analyticsEndpointsAreLimitedToo() throws Exception {
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void redirectsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) mvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());
    }
}
