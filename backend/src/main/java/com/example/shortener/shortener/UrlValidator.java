package com.example.shortener.shortener;

import com.example.shortener.common.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Validates and normalizes user-supplied URLs. Only absolute http/https URLs with ASCII hosts are accepted. */
public final class UrlValidator {
    public static final int MAX_LENGTH = 2048;

    private UrlValidator() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw bad("URL is required");
        String url = raw.strip(); // strips only Unicode whitespace; control characters remain and are rejected below
        if (url.length() > MAX_LENGTH) throw bad("URL is too long (max " + MAX_LENGTH + ")");
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                throw bad("URL contains illegal characters");
            }
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw bad("URL is not valid");
        }
        String scheme = uri.getScheme();
        if (scheme == null) throw bad("URL must be absolute and start with http:// or https://");
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw bad("Only http and https URLs are allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw bad("URL must include a host");
        if (uri.getUserInfo() != null) throw bad("URLs with embedded credentials are not allowed");
        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) throw bad("URL port is out of range");
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) > 127) throw bad("Non-ASCII hosts are not allowed (use punycode)");
        }
        StringBuilder out = new StringBuilder(scheme).append("://").append(host.toLowerCase(Locale.ROOT));
        if (uri.getPort() != -1) out.append(':').append(uri.getPort());
        if (uri.getRawPath() != null) out.append(uri.getRawPath());
        if (uri.getRawQuery() != null) out.append('?').append(uri.getRawQuery());
        if (uri.getRawFragment() != null) out.append('#').append(uri.getRawFragment());
        return out.toString();
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_URL", msg);
    }
}
