package com.example.shortener.shortener;

import com.example.shortener.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LinkService {
    public static final int MAX_PAGE_SIZE = 100;
    static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortLinkRepository repo;
    private final ShortCodeGenerator generator;

    public LinkService(ShortLinkRepository repo, ShortCodeGenerator generator) {
        this.repo = repo;
        this.generator = generator;
    }

    public ShortLink create(String url, String alias) {
        String normalized = UrlValidator.normalize(url);
        if (alias != null && !alias.isBlank()) {
            String a = AliasValidator.validate(alias);
            if (repo.existsByCode(a)) throw taken();
            try {
                return repo.saveAndFlush(new ShortLink(a, normalized, null, "demo-user"));
            } catch (DataIntegrityViolationException e) {
                throw taken();
            }
        }
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generator.next();
            if (repo.existsByCode(code)) continue;
            try {
                return repo.saveAndFlush(new ShortLink(code, normalized, null, "demo-user"));
            } catch (DataIntegrityViolationException ignored) {
                // collision under concurrency: retry with a new code
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_GENERATION_FAILED",
                "Could not generate a unique short code, please retry");
    }

    public ShortLink get(String code) {
        return repo.findByCode(code).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found"));
    }

    public Page<ShortLink> list(int page, int size) {
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repo.findAll(PageRequest.of(Math.max(0, page), s, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, "ALIAS_TAKEN", "Alias is already in use");
    }
}
