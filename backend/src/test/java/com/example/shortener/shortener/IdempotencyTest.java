package com.example.shortener.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.Requirement;
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
@Requirement("001-FR-028")
class IdempotencyTest {
    @Autowired MockMvc mvc;
    @Autowired ShortLinkRepository repo;

    private String body(String url) { return "{\"url\":\"" + url + "\"}"; }

    @Test
    void sameKeyAndPayloadReturnsOriginalLinkWithoutDuplicate() throws Exception {
        String url = "https://example.com/idem-same";
        String first = mvc.perform(post("/api/links").header("Idempotency-Key", "key-same-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(url)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/links").header("Idempotency-Key", "key-same-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(url)))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(code(first), code(second));
        assertEquals(1, repo.findAll().stream().filter(l -> l.getOriginalUrl().equals(url)).count());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        mvc.perform(post("/api/links").header("Idempotency-Key", "key-diff-1")
                .contentType(MediaType.APPLICATION_JSON).content(body("https://example.com/a"))).andExpect(status().isCreated());
        mvc.perform(post("/api/links").header("Idempotency-Key", "key-diff-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://example.com/b")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(0, repo.findAll().stream().filter(l -> l.getOriginalUrl().equals("https://example.com/b")).count());
    }

    @Test
    void withoutKeyEveryRequestCreatesANewLink() throws Exception {
        String url = "https://example.com/no-key";
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body(url))).andExpect(status().isCreated());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body(url))).andExpect(status().isCreated());
        assertEquals(2, repo.findAll().stream().filter(l -> l.getOriginalUrl().equals(url)).count());
    }

    @Test
    void invalidKeyLengthIsRejected() throws Exception {
        mvc.perform(post("/api/links").header("Idempotency-Key", "k".repeat(129))
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://example.com/long-key")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    private static String code(String json) {
        int i = json.indexOf("\"code\":\"") + 8;
        return json.substring(i, json.indexOf('"', i));
    }
}
