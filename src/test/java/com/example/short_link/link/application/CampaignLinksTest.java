package com.example.short_link.link.application;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignLinksTest {

  private static final Instant END = Instant.parse("2026-10-01T00:00:00Z");

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final CampaignLinks campaignLinks = new CampaignLinks(links, cache);

  @Test
  void endingACampaignExpiresTheLinkWithItsPolicy() {
    LinkEntity link = withId(new LinkEntity("https://example.com", "camp001", 7L, null), 3L);
    when(links.findById(3L)).thenReturn(Optional.of(link));

    campaignLinks.expire(new LinkId(3L), END, "https://example.com/next", null);

    assertThat(link.getExpiresAt()).isEqualTo(END);
    assertThat(link.getExpiredRedirectUrl()).isEqualTo("https://example.com/next");
    assertThat(link.getExpiredMessage()).isNull();
  }

  @Test
  void deletingTheLinkDropsItFromTheRedirectCache() {
    LinkEntity link = withId(new LinkEntity("https://example.com", "camp002", 7L, null), 4L);
    when(links.findById(4L)).thenReturn(Optional.of(link));

    campaignLinks.delete(new LinkId(4L));

    verify(links).delete(link);
    verify(cache).evictAfterCommit(link);
  }
}
