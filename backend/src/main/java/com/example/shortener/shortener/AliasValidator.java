package com.example.shortener.shortener;

import com.example.shortener.common.ApiException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class AliasValidator {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Set<String> RESERVED =
            Set.of("api", "actuator", "swagger-ui", "v3", "error", "admin", "health");

    private AliasValidator() {}

    public static String validate(String alias) {
        if (alias == null || !PATTERN.matcher(alias).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALIAS",
                    "Alias must be 3-32 characters: letters, digits, hyphen or underscore");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALIAS", "Alias is reserved");
        }
        return alias;
    }
}
