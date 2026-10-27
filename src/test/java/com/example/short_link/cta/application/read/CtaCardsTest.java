package com.example.short_link.cta.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.support.TestEntities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CtaCardsTest {

  private static final Long AUTHOR = 7L;

  private final CtaRepository ctas = mock(CtaRepository.class);
  private final CtaCards cards = new CtaCards(ctas, new ShortLinkUrlBuilder("https://kurl.me"));

  private CtaEntity stored(long id, Long owner, String url) {
    CtaEntity cta =
        TestEntities.withId(
            new CtaEntity(owner, "Book", url, CtaStyle.PRIMARY, CtaPurpose.BOOKING), id);
    when(ctas.findById(id)).thenReturn(Optional.of(cta));
    return cta;
  }

  @Test
  void theAuthorsCtaBecomesACardThatOpensItsUrl() {
    stored(42L, AUTHOR, "https://cal.com/me");

    assertThat(cards.findOwnedBy(AUTHOR, List.of(42L)))
        .containsExactly(
            Map.entry(
                42L, new CtaCard(42L, "Book", "https://cal.com/me", "PRIMARY", "BOOKING", false)));
  }

  @Test
  void aTrackedCtaOpensItsShortLink() {
    stored(42L, AUTHOR, "https://cal.com/me").trackVia("xy12ab");

    assertThat(cards.findOwnedBy(AUTHOR, List.of(42L)).get(42L).url())
        .isEqualTo("https://kurl.me/xy12ab");
  }

  @Test
  void anotherAuthorsCtaIsLeftOut() {
    stored(42L, 8L, "https://their.site");

    assertThat(cards.findOwnedBy(AUTHOR, List.of(42L))).isEmpty();
  }

  @Test
  void theAuthorsDeletedCtaStaysAsADeletedCard() {
    stored(42L, AUTHOR, "https://cal.com/me").softDelete();

    assertThat(cards.findOwnedBy(AUTHOR, List.of(42L)).get(42L).deleted()).isTrue();
  }

  @Test
  void cardsKeepTheOrderTheIdsCameIn() {
    stored(3L, AUTHOR, "https://a");
    stored(1L, AUTHOR, "https://b");
    when(ctas.findById(2L)).thenReturn(Optional.empty());

    assertThat(cards.findOwnedBy(AUTHOR, List.of(3L, 2L, 1L)).keySet()).containsExactly(3L, 1L);
  }

  @Test
  void aCardAlreadyFoundIsNotReadAgainButAnIdThatFoundNothingIs() {
    stored(42L, AUTHOR, "https://cal.com/me");
    stored(43L, 8L, "https://their.site");
    when(ctas.findById(99L)).thenReturn(Optional.empty());

    cards.findOwnedBy(AUTHOR, List.of(42L, 42L, 43L, 43L, 99L, 99L));

    verify(ctas, times(1)).findById(42L);
    verify(ctas, times(2)).findById(43L);
    verify(ctas, times(2)).findById(99L);
  }
}
