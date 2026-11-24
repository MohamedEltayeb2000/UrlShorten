package com.example.shortener.governance;

import com.example.shortener.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves the acting identity from the X-Demo-User header (demo-grade identity). */
@Component
public class ActorResolver {
    public static final String HEADER = "X-Demo-User";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:@-]{1,64}");

    public Actor resolve(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            return new Actor("demo-user", ActorType.HUMAN);
        }
        String id = raw.trim();
        if (!VALID_ID.matcher(id).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTOR", "Invalid actor identity in " + HEADER);
        }
        return new Actor(id, id.startsWith("agent:") ? ActorType.AGENT : ActorType.HUMAN);
    }
}
