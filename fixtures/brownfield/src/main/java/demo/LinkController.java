package demo;

import java.util.Map;

/** HTTP entry point for creating short links. */
@RestController
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    // body: {"targetUrl": "https://example.com/a/very/long/path"}
    @PostMapping("/links")
    public Map<String, Object> postLink(Map<String, String> body) {
        String targetUrl = body.get("targetUrl");
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("targetUrl is required");
        }
        Link link = linkService.createLink(targetUrl);
        return Map.of(
                "code", link.code(),
                "targetUrl", link.targetUrl(),
                "status", 201);
    }
}
