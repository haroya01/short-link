package com.example.short_link.link.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.link.webhook.exception.WebhookErrorCode;
import com.example.short_link.link.webhook.exception.WebhookException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookOwnershipTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkWebhookRepository hooks = mock(LinkWebhookRepository.class);
  private final WebhookOwnership ownership = new WebhookOwnership(links, hooks);

  private LinkEntity link(Long id, Long ownerId) {
    LinkEntity l = new LinkEntity("https://target", "abc", ownerId, null);
    setField(l, "id", id);
    return l;
  }

  private static void setField(Object o, String name, Object value) {
    try {
      var f = o.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(o, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private LinkWebhookEntity hookFor(Long linkId) {
    return new LinkWebhookEntity(linkId, "https://example.com/h", "secret", "n");
  }

  @Test
  void ownedLinkReturnsWhenOwnerMatches() {
    LinkEntity l = link(1L, 7L);
    when(links.findByShortCode("abc")).thenReturn(Optional.of(l));

    assertThat(ownership.ownedLink(7L, "abc")).isSameAs(l);
  }

  @Test
  void ownedLinkThrowsNotFoundWhenMissing() {
    when(links.findByShortCode("abc")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.ownedLink(7L, "abc"))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.LINK_NOT_FOUND));
  }

  @Test
  void ownedLinkThrowsNotOwnedWhenDifferentOwner() {
    when(links.findByShortCode("abc")).thenReturn(Optional.of(link(1L, 7L)));

    assertThatThrownBy(() -> ownership.ownedLink(99L, "abc"))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.LINK_NOT_OWNED));
  }

  @Test
  void ownedHookReturnsWhenLinkAndHookMatch() {
    LinkEntity l = link(1L, 7L);
    LinkWebhookEntity h = hookFor(1L);
    when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
    when(hooks.findById(99L)).thenReturn(Optional.of(h));

    assertThat(ownership.ownedHook(7L, "abc", 99L)).isSameAs(h);
  }

  @Test
  void ownedHookThrowsWhenHookMissing() {
    LinkEntity l = link(1L, 7L);
    when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
    when(hooks.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.ownedHook(7L, "abc", 99L))
        .isInstanceOfSatisfying(
            WebhookException.class,
            e -> assertThat(e.errorCode()).isEqualTo(WebhookErrorCode.WEBHOOK_NOT_FOUND));
  }

  @Test
  void ownedHookThrowsWhenHookBelongsToDifferentLink() {
    LinkEntity l = link(1L, 7L);
    LinkWebhookEntity hOfOtherLink = hookFor(999L);
    when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
    when(hooks.findById(99L)).thenReturn(Optional.of(hOfOtherLink));

    assertThatThrownBy(() -> ownership.ownedHook(7L, "abc", 99L))
        .isInstanceOfSatisfying(
            WebhookException.class,
            e -> assertThat(e.errorCode()).isEqualTo(WebhookErrorCode.WEBHOOK_NOT_FOUND));
  }
}
