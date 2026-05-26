package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkDestinationOwnershipTest {

  private final LinkRepository linkRepository = mock(LinkRepository.class);
  private final LinkDestinationRepository repository = mock(LinkDestinationRepository.class);
  private final LinkDestinationOwnership ownership =
      new LinkDestinationOwnership(linkRepository, repository);

  @Test
  void ownedLinkReturnsLinkWhenOwned() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(linkRepository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(link));

    assertThat(ownership.ownedLink(42L, new ShortCode("abc1234"))).isSameAs(link);
  }

  @Test
  void ownedLinkThrowsWhenShortCodeNotFound() {
    when(linkRepository.findByShortCode(new ShortCode("nope0001"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.ownedLink(42L, new ShortCode("nope0001")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void ownedLinkThrowsWhenOwnedByDifferentUser() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(linkRepository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(link));

    assertThatThrownBy(() -> ownership.ownedLink(99L, new ShortCode("abc1234")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void ownedDestinationReturnsDestinationWhenLinkOwnsIt() throws Exception {
    LinkEntity link = setId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(linkRepository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(link));
    LinkDestinationEntity dest =
        new LinkDestinationEntity(new LinkId(1L), "https://dest.com", 50, null, null, null, null);
    when(repository.findById(99L)).thenReturn(Optional.of(dest));

    assertThat(ownership.ownedDestination(42L, new ShortCode("abc1234"), 99L)).isSameAs(dest);
  }

  @Test
  void ownedDestinationThrowsWhenDestinationIdNotFound() throws Exception {
    LinkEntity link = setId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(linkRepository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(link));
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.ownedDestination(42L, new ShortCode("abc1234"), 99L))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void ownedDestinationThrowsWhenDestinationBelongsToDifferentLink() throws Exception {
    LinkEntity link = setId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(linkRepository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(link));
    LinkDestinationEntity dest =
        new LinkDestinationEntity(new LinkId(7L), "https://other.com", 50, null, null, null, null);
    when(repository.findById(99L)).thenReturn(Optional.of(dest));

    assertThatThrownBy(() -> ownership.ownedDestination(42L, new ShortCode("abc1234"), 99L))
        .isInstanceOf(DestinationException.class);
  }

  private static <T> T setId(T entity, Long id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
    return entity;
  }
}
