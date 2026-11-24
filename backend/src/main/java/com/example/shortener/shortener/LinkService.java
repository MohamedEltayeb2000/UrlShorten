package com.example.shortener.shortener;

import com.example.shortener.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LinkService {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_IDEMPOTENCY_KEY = 128;
    static final int MAX_CODE_ATTEMPTS = 5;
    static final Duration MAX_EXPIRY = Duration.ofDays(5 * 365L);
    static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /** replayed = true when an Idempotency-Key matched an earlier identical request. */
    public record CreateResult(ShortLink link, boolean replayed) {}

    private final ShortLinkRepository repo;
    private final ShortCodeGenerator generator;
    private final IdempotencyRecordRepository idempotency;

    public LinkService(ShortLinkRepository repo, ShortCodeGenerator generator, IdempotencyRecordRepository idempotency) {
        this.repo = repo;
        this.generator = generator;
        this.idempotency = idempotency;
    }

    public CreateResult create(String url, String alias, Instant expiresAt, String idempotencyKey) {
        String normalized = UrlValidator.normalize(url);
        String cleanAlias = (alias == null || alias.isBlank()) ? null : AliasValidator.validate(alias);
        validateExpiry(expiresAt);

        if (idempotencyKey == null) return new CreateResult(createNew(normalized, cleanAlias, expiresAt), false);

        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1-" + MAX_IDEMPOTENCY_KEY + " characters");
        }
        String fingerprint = fingerprint(normalized, cleanAlias, expiresAt);
        Optional<ShortLink> replay = replay(idempotencyKey, fingerprint);
        if (replay.isPresent()) return new CreateResult(replay.get(), true);

        ShortLink link = createNew(normalized, cleanAlias, expiresAt);
        try {
            idempotency.saveAndFlush(new IdempotencyRecord(idempotencyKey, fingerprint, link.getId(),
                    Instant.now().plus(IDEMPOTENCY_TTL)));
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key won: discard our duplicate and replay the winner.
            repo.delete(link);
            return new CreateResult(replay(idempotencyKey, fingerprint).orElseThrow(() -> race), true);
        }
        return new CreateResult(link, false);
    }

    private Optional<ShortLink> replay(String key, String fingerprint) {
        Optional<IdempotencyRecord> existing = idempotency.findByKey(key);
        if (existing.isEmpty()) return Optional.empty();
        IdempotencyRecord rec = existing.get();
        if (rec.getExpiresAt().isBefore(Instant.now())) {
            idempotency.delete(rec);
            idempotency.flush();
            return Optional.empty();
        }
        if (!rec.getFingerprint().equals(fingerprint)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request");
        }
        return repo.findById(rec.getShortLinkId());
    }

    private ShortLink createNew(String normalized, String alias, Instant expiresAt) {
        if (alias != null) {
            if (repo.existsByCode(alias)) throw taken();
            try {
                return repo.saveAndFlush(new ShortLink(alias, normalized, expiresAt, "demo-user"));
            } catch (DataIntegrityViolationException e) {
                throw taken();
            }
        }
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generator.next();
            if (repo.existsByCode(code)) continue;
            try {
                return repo.saveAndFlush(new ShortLink(code, normalized, expiresAt, "demo-user"));
            } catch (DataIntegrityViolationException ignored) {
                // collision under concurrency: retry with a new code
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_GENERATION_FAILED",
                "Could not generate a unique short code, please retry");
    }

    private static void validateExpiry(Instant expiresAt) {
        if (expiresAt == null) return;
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY", "expiresAt must be in the future");
        }
        if (expiresAt.isAfter(now.plus(MAX_EXPIRY))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY", "expiresAt is more than 5 years away");
        }
    }

    static String fingerprint(String url, String alias, Instant expiresAt) {
        try {
            String raw = url + "\n" + (alias == null ? "" : alias) + "\n" + (expiresAt == null ? "" : expiresAt.toString());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public ShortLink get(String code) {
        return repo.findByCode(code).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found"));
    }

    /** Deactivates a link (soft delete: the row and its click history are kept). Idempotent. */
    public void deactivate(String code) {
        ShortLink link = get(code);
        if (link.getStatus() != ShortLink.Status.INACTIVE) {
            link.setStatus(ShortLink.Status.INACTIVE);
            repo.save(link);
        }
    }

    public Page<ShortLink> list(int page, int size) {
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repo.findAll(PageRequest.of(Math.max(0, page), s, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, "ALIAS_TAKEN", "Alias is already in use");
    }
}
