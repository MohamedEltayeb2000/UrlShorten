package com.example.shortener.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.shortener.common.ApiException;
import com.example.shortener.support.Requirement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Requirement("001-FR-024")
class UrlValidatorTest {

    static Stream<String> hostile() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/security/hostile-urls.txt"),
                StandardCharsets.UTF_8);
        // The corpus stores CRLF as the literal text \r\n; expand it into real control characters.
        return Stream.concat(lines.stream().filter(l -> !l.isBlank()).map(l -> l.replace("\\r\\n", "\r\n")),
                Stream.of("http://example.com/" + "a".repeat(2100), "http://example.com/\u0000"));
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("hostile")
    void hostileInputIsRejected(String url) {
        ApiException ex = assertThrows(ApiException.class, () -> UrlValidator.normalize(url));
        assertEquals("INVALID_URL", ex.code());
    }

    @Tag("security")
    @Test
    void nullAndBlankAreRejected() {
        assertThrows(ApiException.class, () -> UrlValidator.normalize(null));
        assertThrows(ApiException.class, () -> UrlValidator.normalize("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com|https://example.com",
            "HTTP://Example.COM/Path?q=1#frag|http://example.com/Path?q=1#frag",
            "  https://example.com:8443/a/b  |https://example.com:8443/a/b"})
    void validUrlsAreNormalized(String pair) {
        String[] p = pair.split("\\|");
        assertEquals(p[1], UrlValidator.normalize(p[0]));
    }
}
