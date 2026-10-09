package com.example.short_link.campaign.application.write;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BatchPolicyApplierTest {

  private static final Instant END = Instant.parse("2026-10-01T00:00:00Z");

  private final CampaignBatchRepository batches = mock(CampaignBatchRepository.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkExpirationPolicyRepository policies =
      mock(LinkExpirationPolicyRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final BatchPolicyApplier applier =
      new BatchPolicyApplier(batches, links, policies, cache);
  private final List<CampaignBatchEntity> stored = new ArrayList<>();

  @Test
  void aRedirectPolicyDropsEveryChangedLinkFromTheRedirectCache() {
    CampaignEntity campaign = campaign(CampaignPostEndAction.REDIRECT, "https://example.com/next");
    LinkEntity first = batchLink(campaign, 1L, "endone1");
    LinkEntity second = batchLink(campaign, 2L, "endtwo2");

    applier.apply(campaign, END);

    assertThat(first.getExpiredRedirectUrl()).isEqualTo("https://example.com/next");
    assertThat(second.getExpiresAt()).isEqualTo(END);
    verify(cache).evictLinksAfterCommit(List.of(first, second));
  }

  @Test
  void anExpirePolicyDropsTheChangedLinkFromTheRedirectCache() {
    CampaignEntity campaign = campaign(CampaignPostEndAction.EXPIRE, null);
    LinkEntity expired = batchLink(campaign, 3L, "expire3");

    applier.apply(campaign, END);

    verify(cache).evictLinksAfterCommit(List.of(expired));
  }

  @Test
  void aKeepPolicyChangesNoLinkSoNothingIsDropped() {
    CampaignEntity campaign = campaign(CampaignPostEndAction.KEEP, null);
    LinkEntity kept = batchLink(campaign, 4L, "keeper4");

    applier.apply(campaign, END);

    assertThat(kept.getExpiresAt()).isNull();
    verify(cache).evictLinksAfterCommit(List.of());
  }

  private CampaignEntity campaign(CampaignPostEndAction action, String redirectUrl) {
    return withId(
        new CampaignEntity(
            7L,
            "Launch",
            END.minusSeconds(3600),
            END,
            "https://example.com",
            action,
            redirectUrl,
            null),
        11L);
  }

  private LinkEntity batchLink(CampaignEntity campaign, Long linkId, String code) {
    LinkEntity link = withId(new LinkEntity("https://example.com", code, 7L, null), linkId);
    stored.add(
        new CampaignBatchEntity(campaign.getId(), new LinkId(linkId), code, null, null, 1, null));
    when(batches.findByCampaignIdOrderByCreatedAtAsc(campaign.getId())).thenReturn(stored);
    when(links.findById(linkId)).thenReturn(Optional.of(link));
    return link;
  }
}
