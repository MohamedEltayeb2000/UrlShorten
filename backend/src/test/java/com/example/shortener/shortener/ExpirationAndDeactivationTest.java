package com.example.shortener.shortener;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.Requirement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-026")
class ExpirationAndDeactivationTest {
    @Autowired MockMvc mvc;
    @Autowired ShortLinkRepository repo;

    @Test
    void linkWithFutureExpiryRedirectsAndExposesExpiresAt() throws Exception {
        String exp = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/e\",\"alias\":\"exp-future\",\"expiresAt\":\"" + exp + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(get("/exp-future")).andExpect(status().isFound());
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        repo.saveAndFlush(new ShortLink("exp-past", "https://example.com/old", Instant.now().minusSeconds(60), "demo-user"));
        mvc.perform(get("/exp-past")).andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("LINK_EXPIRED"));
    }

    @Test
    void pastOrTooFarExpiryIsRejectedAtCreation() throws Exception {
        String past = Instant.now().minusSeconds(3600).toString();
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_EXPIRY"));
        String far = Instant.now().plus(3650, ChronoUnit.DAYS).toString();
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"" + far + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_EXPIRY"));
    }

    @Test
    void deactivatedLinkNoLongerRedirectsButIsNotHardDeleted() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/d\",\"alias\":\"to-deactivate\"}")).andExpect(status().isCreated());
        mvc.perform(get("/to-deactivate")).andExpect(status().isFound());

        mvc.perform(delete("/api/links/to-deactivate")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/links/to-deactivate")).andExpect(status().isNoContent()); // idempotent

        mvc.perform(get("/to-deactivate")).andExpect(status().isNotFound());
        mvc.perform(get("/api/links/to-deactivate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertTrue(repo.findByCode("to-deactivate").isPresent(), "row must be kept (soft delete)");
        assertEquals(ShortLink.Status.INACTIVE, repo.findByCode("to-deactivate").get().getStatus());
    }

    @Test
    void deactivatingUnknownLinkReturns404() throws Exception {
        mvc.perform(delete("/api/links/never-existed")).andExpect(status().isNotFound());
    }
}
