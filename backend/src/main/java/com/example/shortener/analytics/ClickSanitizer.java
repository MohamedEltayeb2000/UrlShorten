package com.example.shortener.analytics;

import java.net.URI;
import java.util.Locale;

/** Keeps only coarse, bounded data: user agent without control characters, referrer reduced to its origin. */
public final class ClickSanitizer {
    public static final int MAX_USER_AGENT = 200;
    public static final int MAX_REFERRER = 200;

    private ClickSanitizer() {}

    public static String userAgent(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_USER_AGENT; i++) {
            char c = raw.charAt(i);
            if (!Character.isISOControl(c)) sb.append(c);
        }
        String s = sb.toString().strip();
        return s.isEmpty() ? null : s;
    }

    /** Returns scheme://host[:port] for http/https referrers; paths, queries and fragments are dropped. */
    public static String referrer(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) return null;
        try {
            URI u = new URI(raw.strip());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || u.getHost() == null) return null;
            String out = scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() == -1 ? "" : ":" + u.getPort());
            return out.length() > MAX_REFERRER ? null : out;
        } catch (Exception e) {
            return null;
        }
    }
}
