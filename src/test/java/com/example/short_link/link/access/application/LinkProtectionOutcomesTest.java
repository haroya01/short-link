package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.application.dto.LinkProtectionResult;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LinkProtectionOutcomesTest {

  private static final ShortCode CODE = new ShortCode("abc1234");

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final LinkProtectionService service =
      new LinkProtectionService(ownership, encoder, cache);
  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://target", "abc1234", 7L, null), 3L);

  @BeforeEach
  void theLinkIsOwned() {
    when(ownership.requireOwned(7L, CODE)).thenReturn(link);
  }

  @Test
  void aPasswordAndAViewLimitAreSetTogetherAndTheCachedLinkIsDropped() {
    LinkProtectionResult result = service.update(7L, CODE, "s3cret", 5);

    assertThat(result).isEqualTo(new LinkProtectionResult(CODE, true, 5, 0));
    assertThat(encoder.matches("s3cret", link.getPasswordHash())).isTrue();
    assertThat(service.checkPassword(link, "s3cret")).isTrue();
    assertThat(service.checkPassword(link, "wrong")).isFalse();
    assertThat(service.checkPassword(link, null)).isFalse();
    verify(cache).evictAfterCommit(link);
  }

  @Test
  void leavingThePasswordOutKeepsItAndABlankOneRemovesIt() {
    service.update(7L, CODE, "s3cret", null);

    LinkProtectionResult kept = service.update(7L, CODE, null, 3);
    assertThat(kept.passwordProtected()).isTrue();
    assertThat(kept.maxViews()).isEqualTo(3);

    LinkProtectionResult removed = service.update(7L, CODE, " ", null);
    assertThat(removed.passwordProtected()).isFalse();
    assertThat(removed.maxViews()).isNull();
    assertThat(service.checkPassword(link, "anything")).isTrue();
  }
}
