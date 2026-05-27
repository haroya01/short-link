package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CachedLinkLoaderTest {

  @Test
  void loadsRedirectCacheFromNarrowProjectionAndSidecars() {
    LinkRepository repository = mock(LinkRepository.class);
    LinkDestinationRepository destinations = mock(LinkDestinationRepository.class);
    CachedLinkLoader loader = new CachedLinkLoader(repository, destinations);
    ShortCode shortCode = new ShortCode("abc1234");
    Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");

    when(repository.findCachedLinkRowByShortCode(shortCode))
        .thenReturn(
            Optional.of(
                row(
                    7L,
                    shortCode,
                    42L,
                    "https://control.example",
                    expiresAt,
                    "title",
                    "desc",
                    "https://img.example/og.png",
                    "KR,JP",
                    true,
                    10,
                    "ended")));
    when(destinations.findAllByLinkIdOrderByIdAsc(7L))
        .thenReturn(
            List.of(
                new LinkDestinationEntity(
                    new LinkId(7L), "https://variant.example", 50, "A", "KR")));

    var cached = loader.loadByShortCode(shortCode);

    assertThat(cached.linkId()).isEqualTo(new LinkId(7L));
    assertThat(cached.shortCode()).isEqualTo(shortCode);
    assertThat(cached.userId()).isEqualTo(42L);
    assertThat(cached.originalUrl()).isEqualTo("https://control.example");
    assertThat(cached.expiresAt()).isEqualTo(expiresAt);
    assertThat(cached.ogTitle()).isEqualTo("title");
    assertThat(cached.blockedCountries()).isEqualTo("KR,JP");
    assertThat(cached.passwordRequired()).isTrue();
    assertThat(cached.maxViews()).isEqualTo(10);
    assertThat(cached.expiredMessage()).isEqualTo("ended");
    assertThat(cached.variants()).hasSize(1);
    assertThat(cached.variants().getFirst().url()).isEqualTo("https://variant.example");
  }

  private static LinkRepository.CachedLinkRow row(
      Long id,
      ShortCode shortCode,
      Long userId,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage,
      String blockedCountries,
      Boolean passwordRequired,
      Integer maxViews,
      String expiredMessage) {
    return new LinkRepository.CachedLinkRow() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public ShortCode getShortCode() {
        return shortCode;
      }

      @Override
      public Long getUserId() {
        return userId;
      }

      @Override
      public String getOriginalUrl() {
        return originalUrl;
      }

      @Override
      public Instant getExpiresAt() {
        return expiresAt;
      }

      @Override
      public String getOgTitle() {
        return ogTitle;
      }

      @Override
      public String getOgDescription() {
        return ogDescription;
      }

      @Override
      public String getOgImage() {
        return ogImage;
      }

      @Override
      public String getBlockedCountries() {
        return blockedCountries;
      }

      @Override
      public Boolean getPasswordRequired() {
        return passwordRequired;
      }

      @Override
      public Integer getMaxViews() {
        return maxViews;
      }

      @Override
      public String getExpiredMessage() {
        return expiredMessage;
      }
    };
  }
}
