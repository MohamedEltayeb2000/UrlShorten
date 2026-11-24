package com.example.shortener.shortener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.shortener.common.ApiException;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

@Requirement("001-FR-028")
class CollisionRetryTest {
    ShortLinkRepository repo = mock(ShortLinkRepository.class);
    ShortCodeGenerator gen = mock(ShortCodeGenerator.class);
    LinkService service = new LinkService(repo, gen, mock(IdempotencyRecordRepository.class));

    @Test
    void givesUpAfterBoundedAttemptsWhenEveryCodeCollides() {
        when(gen.next()).thenReturn("same-code");
        when(repo.existsByCode(anyString())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.create("https://example.com", null, null, null));
        assertEquals("CODE_GENERATION_FAILED", ex.code());
        verify(gen, times(LinkService.MAX_CODE_ATTEMPTS)).next();
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void retriesWithNewCodeAfterCollisionThenSucceeds() {
        when(gen.next()).thenReturn("taken01", "free002");
        when(repo.existsByCode("taken01")).thenReturn(true);
        when(repo.existsByCode("free002")).thenReturn(false);
        when(repo.saveAndFlush(any(ShortLink.class))).thenAnswer(i -> i.getArgument(0));

        LinkService.CreateResult r = service.create("https://example.com", null, null, null);
        assertEquals("free002", r.link().getCode());
        verify(gen, times(2)).next();
    }

    @Test
    void retriesWhenDatabaseUniqueConstraintRaceHappens() {
        when(gen.next()).thenReturn("race001", "race002");
        when(repo.existsByCode(anyString())).thenReturn(false);
        when(repo.saveAndFlush(any(ShortLink.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(i -> i.getArgument(0));

        assertEquals("race002", service.create("https://example.com", null, null, null).link().getCode());
    }
}
