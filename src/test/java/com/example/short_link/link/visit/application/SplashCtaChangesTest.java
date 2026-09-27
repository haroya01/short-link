package com.example.short_link.link.visit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SplashCtaChangesTest {

  @Test
  void everyLinkShowingTheButtonIsRefreshed() {
    LinkVisitOptionRepository options = mock(LinkVisitOptionRepository.class);
    LinkCacheEviction eviction = mock(LinkCacheEviction.class);
    when(options.findShortCodesUsingSplashCta(9L))
        .thenReturn(List.of(new ShortCode("abc1"), new ShortCode("abc2")));

    new SplashCtaChanges(options, eviction).ctaChanged(9L);

    verify(eviction).evictAfterCommit(new ShortCode("abc1"));
    verify(eviction).evictAfterCommit(new ShortCode("abc2"));
  }
}
