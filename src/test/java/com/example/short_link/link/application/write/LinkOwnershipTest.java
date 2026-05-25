package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkOwnershipTest {

  private final LinkRepository repository = mock(LinkRepository.class);
  private final LinkOwnership ownership = new LinkOwnership(repository);

  @Test
  void requireOwnedReturnsLinkWhenOwnedByUser() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

    assertThat(ownership.requireOwned(42L, "abc1234")).isSameAs(link);
  }

  @Test
  void requireOwnedThrowsWhenShortCodeNotFound() {
    when(repository.findByShortCode("nope0001")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.requireOwned(42L, "nope0001"))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void requireOwnedThrowsWhenLinkOwnedByDifferentUser() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

    assertThatThrownBy(() -> ownership.requireOwned(99L, "abc1234"))
        .isInstanceOf(LinkException.class);
  }
}
