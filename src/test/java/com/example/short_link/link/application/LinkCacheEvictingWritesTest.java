package com.example.short_link.link.application;

import static com.example.short_link.support.TestEntities.withId;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.write.ClaimAnonymousLinksCommand;
import com.example.short_link.link.application.write.ClaimAnonymousLinksUseCase;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.application.write.RefreshTokenStore;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkCacheEvictingWritesTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);

  @Test
  void claimingAnonymousLinksDropsEachClaimedLink() {
    when(links.findAllByClaimTokenInAndUserIdIsNull(List.of("t1", "t2")))
        .thenReturn(List.of(link(1L, "claim01", null), link(2L, "claim02", null)));
    ClaimAnonymousLinksUseCase claim =
        new ClaimAnonymousLinksUseCase(links, new SimpleMeterRegistry(), cache);

    claim.execute(ClaimAnonymousLinksCommand.of(9L, List.of("t1", "t2")));

    verify(cache).evictAllAfterCommit(List.of(new ShortCode("claim01"), new ShortCode("claim02")));
  }

  @Test
  void deletingACampaignBatchDropsItsLink() {
    CampaignBatchRepository batches = mock(CampaignBatchRepository.class);
    CampaignBatchEntity batch =
        new CampaignBatchEntity(5L, new LinkId(3L), "Station", null, null, 1, null);
    when(batches.findById(4L)).thenReturn(Optional.of(batch));
    when(links.findById(3L)).thenReturn(Optional.of(link(3L, "batch03", 9L)));
    CampaignBatchService service =
        new CampaignBatchService(
            batches, links, mock(CreateLinkUseCase.class), mock(CampaignQueryService.class), cache);

    service.delete(5L, 4L, 9L);

    verify(cache).evictAfterCommit(new ShortCode("batch03"));
  }

  @Test
  void hardDeletingAnAccountDropsAllOfItsLinks() {
    UserRepository users = mock(UserRepository.class);
    when(users.existsById(9L)).thenReturn(true);
    when(links.findAllByUserIdOrderByCreatedAtDesc(9L))
        .thenReturn(List.of(link(6L, "owned06", 9L), link(7L, "owned07", 9L)));
    when(links.deleteByUserId(9L)).thenReturn(2);
    UserDeletionService deletion =
        new UserDeletionService(
            users,
            links,
            mock(ClickEventRepository.class),
            mock(FollowRepository.class),
            mock(BlockRepository.class),
            mock(MuteRepository.class),
            mock(WebPushSubscriptionRepository.class),
            List.of(),
            mock(RefreshTokenStore.class),
            mock(DeviceTokenRepository.class),
            new SimpleMeterRegistry(),
            mock(AuditLogService.class),
            event -> {},
            cache);

    deletion.hardDelete(9L);

    verify(links).deleteByUserId(9L);
    verify(cache).evictAllAfterCommit(List.of(new ShortCode("owned06"), new ShortCode("owned07")));
  }

  private static LinkEntity link(Long id, String code, Long owner) {
    return withId(new LinkEntity("https://example.com/" + code, code, owner, null), id);
  }
}
