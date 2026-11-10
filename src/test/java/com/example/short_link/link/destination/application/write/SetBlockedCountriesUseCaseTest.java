package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import org.junit.jupiter.api.Test;

class SetBlockedCountriesUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkCacheEviction linkCacheEviction = mock(LinkCacheEviction.class);
  private final SetBlockedCountriesUseCase useCase =
      new SetBlockedCountriesUseCase(ownership, linkCacheEviction);

  private LinkEntity link() {
    LinkEntity l = new LinkEntity("https://target", "abc", 7L, null);
    when(ownership.ownedLink(7L, new ShortCode("abc"))).thenReturn(l);
    return l;
  }

  @Test
  void executeUpdatesBlockedCountriesOnLink() {
    LinkEntity l = link();

    useCase.execute(7L, new ShortCode("abc"), "kr,JP");

    assertThat(l.getBlockedCountries()).isEqualTo("KR,JP");
  }

  @Test
  void executeDropsTheRedirectCacheAfterCommit() {
    link();

    useCase.execute(7L, new ShortCode("abc"), "KR,JP");

    verify(linkCacheEviction).evictAfterCommit(new ShortCode("abc"));
  }

  @Test
  void executeReturnsSameLinkEntity() {
    LinkEntity l = link();

    assertThat(useCase.execute(7L, new ShortCode("abc"), "KR,JP")).isSameAs(l);
  }
}
