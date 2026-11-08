package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimAnonymousLinksQuotaTest {

  private static final Instant TOMORROW = Instant.parse("2026-11-10T00:00:00Z");

  private final LinkRepository repository = mock(LinkRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final ClaimAnonymousLinksUseCase claims =
      new ClaimAnonymousLinksUseCase(repository, meters, cache, 3L);

  private LinkEntity anonymous(String code) {
    LinkEntity link = new LinkEntity("https://example.com/" + code, code, null, TOMORROW);
    link.setClaimToken("token-" + code);
    return link;
  }

  @Test
  void claimedLinksBecomeTheUsersPermanentLinksAndLeaveTheCache() {
    LinkEntity first = anonymous("anon001");
    LinkEntity second = anonymous("anon002");
    List<String> tokens = List.of("token-anon001", "token-anon002", "token-gone");
    when(repository.findAllByClaimTokenInAndUserIdIsNull(tokens))
        .thenReturn(List.of(first, second));
    when(repository.countByUserId(7L)).thenReturn(0L);

    ClaimResult result = claims.execute(new ClaimAnonymousLinksCommand(7L, tokens));

    assertThat(result).isEqualTo(new ClaimResult(2, 1));
    assertThat(List.of(first, second))
        .allSatisfy(
            link -> {
              assertThat(link.getUserId()).isEqualTo(7L);
              assertThat(link.getClaimToken()).isNull();
              assertThat(link.getExpiresAt()).isNull();
            });
    verify(cache).evictAllAfterCommit(List.of(new ShortCode("anon001"), new ShortCode("anon002")));
    assertThat(meters.counter("link.claim", "result", "ok").count()).isEqualTo(2.0);
    assertThat(meters.counter("link.claim", "result", "skipped").count()).isEqualTo(1.0);
  }

  @Test
  void onlyAsManyLinksAsTheQuotaHasRoomForAreClaimed() {
    LinkEntity first = anonymous("anon001");
    LinkEntity second = anonymous("anon002");
    List<String> tokens = List.of("token-anon001", "token-anon002");
    when(repository.findAllByClaimTokenInAndUserIdIsNull(tokens))
        .thenReturn(List.of(first, second));
    when(repository.countByUserId(7L)).thenReturn(2L);

    ClaimResult result = claims.execute(new ClaimAnonymousLinksCommand(7L, tokens));

    assertThat(result).isEqualTo(new ClaimResult(1, 1));
    assertThat(first.getUserId()).isEqualTo(7L);
    assertThat(second.getUserId()).isNull();
  }

  @Test
  void aUserAlreadyOverTheQuotaClaimsNothing() {
    LinkEntity first = anonymous("anon001");
    List<String> tokens = List.of("token-anon001");
    when(repository.findAllByClaimTokenInAndUserIdIsNull(tokens)).thenReturn(List.of(first));
    when(repository.countByUserId(7L)).thenReturn(5L);

    ClaimResult result = claims.execute(new ClaimAnonymousLinksCommand(7L, tokens));

    assertThat(result).isEqualTo(new ClaimResult(0, 1));
    assertThat(first.getUserId()).isNull();
  }
}
