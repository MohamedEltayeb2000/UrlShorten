package com.example.shortener.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
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
@Tag("security")
@Requirement("001-FR-014")
class RunSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void oversizedAndControlCharacterRequirementsAreRejected() throws Exception {
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUIREMENT"));
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"bad\\u0000byte\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scriptLikeRequirementsAreStoredAsInertDataAndReturnedAsJson() throws Exception {
        String xss = "<script>alert(1)</script> and '; DROP TABLE agent_run;--";
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"" + xss.replace("'", "\\u0027") + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void productionCodeNeverSpawnsProcessesOrWritesTheRepository() throws IOException {
        List<String> forbidden = List.of("ProcessBuilder", "Runtime.getRuntime", ".exec(", "Files.write(", "FileWriter(", "FileOutputStream(");
        Path src = RepoFiles.file("backend/src/main/java");
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(f);
                for (String bad : forbidden) {
                    assertFalse(text.contains(bad), f.getFileName() + " uses forbidden API " + bad);
                }
            }
        }
    }
}
