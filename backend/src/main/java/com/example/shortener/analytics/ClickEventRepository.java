package com.example.shortener.analytics;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortLinkId(Long shortLinkId);

    List<ClickEvent> findByOccurredAtAfter(Instant since);

    List<ClickEvent> findByShortLinkIdAndOccurredAtAfter(Long shortLinkId, Instant since);

    @Query("select c.shortLinkId, count(c) from ClickEvent c where c.shortLinkId in :ids group by c.shortLinkId")
    List<Object[]> countByLinkIds(@Param("ids") Collection<Long> ids);

    @Query("select l.code, count(c) from ClickEvent c, ShortLink l where l.id = c.shortLinkId "
            + "group by l.code order by count(c) desc, l.code asc")
    List<Object[]> topLinks(Pageable pageable);

    @Query(value = "select new com.example.shortener.analytics.ClickView(l.code, c.occurredAt, c.referrer, c.userAgent) "
            + "from ClickEvent c, ShortLink l where l.id = c.shortLinkId order by c.occurredAt desc, c.id desc",
            countQuery = "select count(c) from ClickEvent c")
    Page<ClickView> recent(Pageable pageable);

    @Query(value = "select new com.example.shortener.analytics.ClickView(l.code, c.occurredAt, c.referrer, c.userAgent) "
            + "from ClickEvent c, ShortLink l where l.id = c.shortLinkId and l.code = :code "
            + "order by c.occurredAt desc, c.id desc",
            countQuery = "select count(c) from ClickEvent c, ShortLink l where l.id = c.shortLinkId and l.code = :code")
    Page<ClickView> recentForCode(@Param("code") String code, Pageable pageable);
}
