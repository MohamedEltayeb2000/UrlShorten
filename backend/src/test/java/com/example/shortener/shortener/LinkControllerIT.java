package com.example.shortener.shortener;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@Requirement("001-FR-023")
@Requirement("001-FR-024")
@Requirement("001-FR-025")
@Requirement("001-FR-026")
@Requirement("001-FR-029")
class LinkControllerIT {

    @Autowired MockMvc mvc;

    private String create(String json) throws Exception {
        return mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createReturns201WithCodeAndShortUrl() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/page\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code", hasLength(7)))
                .andExpect(jsonPath("$.shortUrl", containsString("/")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/page"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void invalidUrlReturns400WithStandardErrorBody() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_URL"))
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    void missingUrlReturns400ValidationError() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void takenAliasReturns409AndInvalidAliasReturns400() throws Exception {
        String body = "{\"url\":\"https://example.com\",\"alias\":\"my-alias\"}";
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("my-alias"));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALIAS_TAKEN"));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"alias\":\"bad alias!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ALIAS"));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"alias\":\"api\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirectReturns302ToStoredUrl() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/target\",\"alias\":\"redir-test\"}"));
        mvc.perform(get("/redir-test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/doesnotexist1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"));
        mvc.perform(get("/api/links/doesnotexist1")).andExpect(status().isNotFound());
    }

    @Test
    void getAndListAreAvailableAndPaginated() throws Exception {
        for (int i = 0; i < 3; i++) {
            create("{\"url\":\"https://example.com/p" + i + "\"}");
        }
        mvc.perform(get("/api/links").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(3)));
        mvc.perform(get("/api/links").param("size", "100000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/meta\",\"alias\":\"meta-link\"}"));
        mvc.perform(get("/api/links/meta-link"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalUrl").value("https://example.com/meta"));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsAreServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/links']").exists());
    }
}
