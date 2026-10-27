package com.example.short_link.link.application;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LinkAccountDataTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickEventRepository clicks = mock(ClickEventRepository.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final LinkAccountData data = new LinkAccountData(links, clicks, cache);

  private static LinkEntity link(long id, String code) {
    return withId(new LinkEntity("https://example.com/" + code, code, 9L, null), id);
  }

  @Test
  void erasingRemovesTheClicksBeforeTheLinksAndThenDropsThemFromTheCache() {
    when(links.findAllByUserIdOrderByCreatedAtDesc(9L))
        .thenReturn(List.of(link(6L, "owned06"), link(7L, "owned07")));
    when(links.deleteByUserId(9L)).thenReturn(2);

    assertThat(data.eraseOwned(9L)).isEqualTo(2);

    InOrder order = inOrder(clicks, links, cache);
    order.verify(clicks).deleteByLinkIds(List.of(6L, 7L));
    order.verify(links).deleteByUserId(9L);
    order
        .verify(cache)
        .evictAllAfterCommit(List.of(new ShortCode("owned06"), new ShortCode("owned07")));
  }

  @Test
  void anAccountWithoutLinksTouchesNoClicks() {
    when(links.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());

    data.eraseOwned(9L);

    verify(clicks, never()).deleteByLinkIds(any());
    verify(links).deleteByUserId(9L);
  }

  @Test
  void theExportCarriesEachLinkAndNoClicksWhenThereAreNoLinks() {
    when(links.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(link(6L, "owned06")));
    when(clicks.findAllByLinkIdInOrderByClickedAtAsc(List.of(6L))).thenReturn(List.of());

    LinkAccountData.OwnedLinks owned = data.export(9L);

    assertThat(owned.links())
        .extracting(LinkAccountData.ExportedLink::shortCode)
        .containsExactly("owned06");
    assertThat(owned.clicks()).isEmpty();
  }
}
