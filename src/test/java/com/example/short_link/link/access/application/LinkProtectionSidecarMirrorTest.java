package com.example.short_link.link.access.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.application.write.LinkSidecars;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LinkProtectionSidecarMirrorTest {

  private static final ShortCode CODE = new ShortCode("abc1234");

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final LinkSidecars sidecars = mock(LinkSidecars.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final LinkProtectionService service =
      new LinkProtectionService(ownership, sidecars, encoder, mock(LinkCacheEviction.class));
  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://target", "abc1234", 7L, null), 3L);

  @BeforeEach
  void theLinkIsOwned() {
    when(ownership.requireOwned(7L, CODE)).thenReturn(link);
    when(encoder.encode("secret")).thenReturn("hash");
  }

  @Test
  void changingOnlyTheViewLimitMirrorsTheViewLimitAlone() {
    service.update(7L, CODE, null, 5);

    verify(sidecars).mirrorMaxViews(link);
    verify(sidecars, never()).mirrorPasswordAndMaxViews(any());
  }

  @Test
  void settingAPasswordMirrorsTheHashWithTheViewLimit() {
    service.update(7L, CODE, "secret", 5);

    verify(sidecars).mirrorPasswordAndMaxViews(link);
    verify(sidecars, never()).mirrorMaxViews(any());
  }
}
