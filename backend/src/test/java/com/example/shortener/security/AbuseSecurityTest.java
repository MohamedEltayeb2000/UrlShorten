package com.example.shortener.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.analytics.ClickEvent;
import com.example.shortener.analytics.ClickEventRepository;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Tag;
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
@Tag("security")
@Requirement("001-FR-024")
@Requirement("001-FR-025")
@Requirement("001-FR-028")
class AbuseSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ClickEventRepository clicks;

    private void create(String json) throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void redirectIgnoresAttackerSuppliedTargetsInTheRequest() throws Exception {
        create("{\"url\":\"https://example.com/safe\",\"alias\":\"open-redir\"}");
        mvc.perform(get("/open-redir").param("next", "https://evil.example").param("url", "https://evil.example")
                        .header("Host", "evil.example"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/safe"));
    }

    @Test
    void oversizedAndMaliciousFieldsAreRejected() throws Exception {
        String longUrl = "https://example.com/" + "a".repeat(2100);
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + longUrl + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"alias\":\"" + "x".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest());
        for (String alias : new String[] {"a'; DROP TABLE short_link;--", "../../etc/passwd", "<script>", "ab", "with space"}) {
            mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com\",\"alias\":\"" + alias.replace("\\", "\\\\") + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void errorResponsesLeakNoInternals() throws Exception {
        String body = mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"file:///etc/passwd\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        for (String leak : new String[] {"Exception", "at com.example", "org.springframework", "jdbc", "SELECT"}) {
            assertFalse(body.contains(leak), "error body leaked: " + leak);
        }
    }

    @Test
    void storedClickDataIsBoundedAndFreeOfControlCharacters() throws Exception {
        create("{\"url\":\"https://example.com/ua\",\"alias\":\"ua-check\"}");
        mvc.perform(get("/ua-check").header(HttpHeaders.USER_AGENT, "Agent\u0007" + "Z".repeat(600))
                .header(HttpHeaders.REFERER, "javascript:alert(1)"));
        ClickEvent last = clicks.findAll().stream().reduce((a, b) -> b).orElseThrow();
        assertTrue(last.getUserAgent().length() <= 200);
        assertTrue(last.getUserAgent().chars().noneMatch(Character::isISOControl));
        assertNull(last.getReferrer(), "non-http referrers are not stored");
    }

    @Test
    void analyticsReadEndpointsDoNotExposeVisitorAddressesOrFullReferrers() throws Exception {
        create("{\"url\":\"https://example.com/priv\",\"alias\":\"privacy-ck\"}");
        mvc.perform(get("/privacy-ck").header(HttpHeaders.REFERER, "https://ref.example/a/b?session=SECRET123"));
        String json = mvc.perform(get("/api/analytics/links/privacy-ck")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("SECRET123"));
        assertFalse(json.contains("127.0.0.1"));
        assertTrue(json.contains("https://ref.example"));
    }
}
