package com.example.short_link.cta.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.visit.application.SplashCta;
import com.example.short_link.support.TestEntities;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CtaSplashCatalogTest {

  private final CtaRepository ctas = mock(CtaRepository.class);
  private final CtaSplashCatalog catalog =
      new CtaSplashCatalog(ctas, new ShortLinkUrlBuilder("https://kurl.me"));

  private CtaEntity cta(long ownerId) {
    CtaEntity cta =
        new CtaEntity(
            ownerId, "Book a call", "https://cal.example/me", CtaStyle.PRIMARY, CtaPurpose.BOOKING);
    TestEntities.withId(cta, 5L);
    when(ctas.findById(5L)).thenReturn(Optional.of(cta));
    return cta;
  }

  @Test
  void aTrackedCtaSendsVisitorsThroughItsShortLink() {
    cta(7L).trackVia("abc123");

    assertThat(catalog.findOwned(7L, 5L))
        .contains(new SplashCta(5L, "Book a call", "https://kurl.me/abc123"));
  }

  @Test
  void anUntrackedCtaSendsVisitorsStraightToItsAddress() {
    cta(7L);

    assertThat(catalog.findOwned(7L, 5L))
        .contains(new SplashCta(5L, "Book a call", "https://cal.example/me"));
  }

  @Test
  void someoneElsesCtaIsNotShownOnTheLink() {
    cta(99L);

    assertThat(catalog.findOwned(7L, 5L)).isEmpty();
  }

  @Test
  void aDeletedCtaIsNotShownOnTheLink() {
    cta(7L).softDelete();

    assertThat(catalog.findOwned(7L, 5L)).isEmpty();
  }
}
