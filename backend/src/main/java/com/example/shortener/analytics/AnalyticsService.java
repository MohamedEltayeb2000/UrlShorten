package com.example.shortener.analytics;

import com.example.shortener.common.ApiException;
import com.example.shortener.shortener.ShortLink;
import com.example.shortener.shortener.ShortLinkRepository;
import com.example.shortener.shortener.dto.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    public static final int WINDOW_DAYS = 30;
    public static final int TOP_LINKS = 5;

    public record DayCount(LocalDate date, long clicks) {}
    public record TopLink(String code, long clicks) {}
    public record Summary(long totalLinks, long totalClicks, List<DayCount> clicksOverTime, List<TopLink> topLinks,
                          PageResponse<ClickView> recentEvents) {}
    public record LinkAnalytics(String code, long totalClicks, List<DayCount> clicksOverTime,
                                PageResponse<ClickView> recentEvents) {}

    private final ClickEventRepository clicks;
    private final ShortLinkRepository links;

    public AnalyticsService(ClickEventRepository clicks, ShortLinkRepository links) {
        this.clicks = clicks;
        this.links = links;
    }

    public void recordClick(ShortLink link, String referrer, String userAgent, String requestId) {
        clicks.save(new ClickEvent(link.getId(), Instant.now(), ClickSanitizer.referrer(referrer),
                ClickSanitizer.userAgent(userAgent), requestId));
    }

    public Summary summary(int page, int size) {
        Instant since = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);
        List<TopLink> top = clicks.topLinks(PageRequest.of(0, TOP_LINKS)).stream()
                .map(r -> new TopLink((String) r[0], (Long) r[1])).toList();
        return new Summary(links.count(), clicks.count(), overTime(clicks.findByOccurredAtAfter(since), since), top,
                PageResponse.of(clicks.recent(pageable(page, size)), v -> v));
    }

    public LinkAnalytics forLink(String code, int page, int size) {
        ShortLink link = links.findByCode(code).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found"));
        Instant since = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);
        return new LinkAnalytics(code, clicks.countByShortLinkId(link.getId()),
                overTime(clicks.findByShortLinkIdAndOccurredAtAfter(link.getId(), since), since),
                PageResponse.of(clicks.recentForCode(code, pageable(page, size)), v -> v));
    }

    /** Click totals for a set of link IDs (used to show a clicks column in the link list). */
    public Map<Long, Long> clickCounts(Collection<Long> linkIds) {
        if (linkIds.isEmpty()) return Map.of();
        return clicks.countByLinkIds(linkIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private static org.springframework.data.domain.Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }

    private static List<DayCount> overTime(List<ClickEvent> events, Instant since) {
        Map<LocalDate, Long> perDay = new TreeMap<>();
        LocalDate start = since.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) perDay.put(d, 0L);
        for (ClickEvent e : events) perDay.merge(e.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate(), 1L, Long::sum);
        return perDay.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();
    }
}
