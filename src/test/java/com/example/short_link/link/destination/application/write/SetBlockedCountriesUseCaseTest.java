package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SetBlockedCountriesUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkExpirationPolicyRepository policies =
      mock(LinkExpirationPolicyRepository.class);
  private final SetBlockedCountriesUseCase useCase =
      new SetBlockedCountriesUseCase(ownership, policies);

  private LinkEntity link() {
    LinkEntity l = new LinkEntity("https://target", "abc", 7L, null);
    try {
      var f = LinkEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(l, 1L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    when(ownership.ownedLink(7L, new ShortCode("abc"))).thenReturn(l);
    return l;
  }

  @Test
  void executeUpdatesBlockedCountriesOnLink() {
    LinkEntity l = link();
    when(policies.findById(1L)).thenReturn(Optional.empty());

    useCase.execute(7L, new ShortCode("abc"), "KR,JP");

    assertThat(l.getBlockedCountries()).isNotNull();
  }

  @Test
  void executeSavesNewPolicyWhenAbsent() {
    link();
    when(policies.findById(1L)).thenReturn(Optional.empty());

    useCase.execute(7L, new ShortCode("abc"), "KR,JP");

    verify(policies, times(1)).save(any(LinkExpirationPolicyEntity.class));
  }

  @Test
  void executeSavesExistingPolicyWhenPresent() {
    link();
    LinkExpirationPolicyEntity existing = new LinkExpirationPolicyEntity(new LinkId(1L));
    when(policies.findById(1L)).thenReturn(Optional.of(existing));

    useCase.execute(7L, new ShortCode("abc"), "KR,JP");

    verify(policies, times(1)).save(existing);
  }

  @Test
  void executeReturnsSameLinkEntity() {
    LinkEntity l = link();
    when(policies.findById(1L)).thenReturn(Optional.empty());

    assertThat(useCase.execute(7L, new ShortCode("abc"), "KR,JP")).isSameAs(l);
  }
}
