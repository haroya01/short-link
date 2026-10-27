package com.example.short_link.link.application;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.application.write.LinkSidecars;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampaignLinksTest {

  private static final Instant END = Instant.parse("2026-10-01T00:00:00Z");

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkExpirationPolicyRepository policies =
      mock(LinkExpirationPolicyRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final CampaignLinks campaignLinks =
      new CampaignLinks(
          links,
          new LinkSidecars(
              mock(LinkAccessControlRepository.class),
              policies,
              mock(LinkOgMetadataRepository.class)),
          cache);

  @Test
  void endingACampaignExpiresTheLinkAndMirrorsItsPolicyOntoTheSidecar() {
    LinkEntity link = withId(new LinkEntity("https://example.com", "camp001", 7L, null), 3L);
    when(links.findById(3L)).thenReturn(Optional.of(link));
    when(policies.findById(3L)).thenReturn(Optional.empty());

    campaignLinks.expire(new LinkId(3L), END, "https://example.com/next", null);

    assertThat(link.getExpiresAt()).isEqualTo(END);
    ArgumentCaptor<LinkExpirationPolicyEntity> saved =
        ArgumentCaptor.forClass(LinkExpirationPolicyEntity.class);
    verify(policies).save(saved.capture());
    assertThat(saved.getValue().getExpiredRedirectUrl()).isEqualTo("https://example.com/next");
    assertThat(saved.getValue().getExpiredMessage()).isNull();
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
