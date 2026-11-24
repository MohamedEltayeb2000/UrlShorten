package com.example.shortener.shortener;

import com.example.shortener.shortener.dto.CreateLinkRequest;
import com.example.shortener.shortener.dto.LinkResponse;
import com.example.shortener.shortener.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkController {
    private final LinkService service;
    private final String baseUrl;

    public LinkController(LinkService service,
                          @Value("${shortener.public-base-url:http://localhost:8080}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest req) {
        ShortLink link = service.create(req.url(), req.alias());
        return ResponseEntity.created(URI.create("/api/links/" + link.getCode()))
                .body(LinkResponse.from(link, baseUrl));
    }

    @GetMapping
    public PageResponse<LinkResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(page, size), l -> LinkResponse.from(l, baseUrl));
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return LinkResponse.from(service.get(code), baseUrl);
    }
}
