package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import com.example.short_link.support.TestEntities;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileLinksTest {

  private static final Instant OPENS = Instant.parse("2026-11-01T00:00:00Z");

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickTotalsReader clicks = mock(ClickTotalsReader.class);
  private final ProfileLinks profileLinks =
      new ProfileLinks(links, clicks, new ShortLinkUrlBuilder("https://kurl.me"));

  private record Row(LinkEntity getLink, Instant getOpensAt, Boolean getPasswordRequired)
      implements ProfileLinkRow {}

  private record Count(Long getLinkId, Long getCount) implements LinkClickCount {}

  @Test
  void aCardCarriesWhatTheProfileShowsAndItsClicks() {
    LinkEntity open =
        TestEntities.withId(new LinkEntity("https://open.example", "open001", 7L, null), 1L);
    open.applyOgMetadata("Fetched", null, "https://img", OPENS);
    open.changeOgOverride("Mine", null, null);
    open.setProfileOrder(2);
    open.featureOnProfile();
    LinkEntity locked =
        TestEntities.withId(new LinkEntity("https://locked.example", "lock001", 7L, null), 2L);
    locked.setMaxViews(5);
    when(links.findPublicProfileLinks(7L))
        .thenReturn(List.of(new Row(open, null, null), new Row(locked, OPENS, true)));
    when(clicks.countsByLinkIds(List.of(1L, 2L))).thenReturn(List.of(new Count(1L, 9L)));

    List<ProfileLinkCard> cards = profileLinks.publicCards(7L);

    assertThat(cards.get(0))
        .isEqualTo(
            new ProfileLinkCard(
                new ShortCode("open001"),
                "https://kurl.me/open001",
                "https://open.example",
                new ProfileLinkCard.Preview("Mine", "https://img", "Mine"),
                false,
                true,
                2,
                9L,
                new ProfileLinkCard.Window(null, null, false)));
    assertThat(cards.get(0).window().limits()).isFalse();
    assertThat(cards.get(1).passwordRequired()).isTrue();
    assertThat(cards.get(1).clicks()).isZero();
    assertThat(cards.get(1).window()).isEqualTo(new ProfileLinkCard.Window(OPENS, null, true));
    assertThat(cards.get(1).window().limits()).isTrue();
  }

  @Test
  void aProfileWithoutLinksDoesNotAskForClicks() {
    when(links.findPublicProfileLinks(7L)).thenReturn(List.of());

    assertThat(profileLinks.publicCards(7L)).isEmpty();
    verifyNoInteractions(clicks);
  }

  @Test
  void spentLinksComeFromTheViewLimitQuery() {
    List<ShortCode> codes = List.of(new ShortCode("lock001"));
    when(links.findShortCodesWithViewLimitReached(codes)).thenReturn(codes);

    assertThat(profileLinks.viewLimitReached(codes)).containsExactly(new ShortCode("lock001"));
  }
}
