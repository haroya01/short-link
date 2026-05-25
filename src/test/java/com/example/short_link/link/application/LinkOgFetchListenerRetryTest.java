package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

class LinkOgFetchListenerRetryTest {

  @Test
  void firstFailureMarksRetryableWhenAttemptsRemaining() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(OgMetadata.empty());

    LinkRepository repo = mock(LinkRepository.class);
    LinkEntity entity = link("retry001", "https://example.com/x");
    when(repo.findByShortCode("retry001")).thenReturn(Optional.of(entity));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

    LinkOgFetchListener listener =
        new LinkOgFetchListener(
            scraper,
            repo,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new NoOpCacheManager(),
            new OgFetchProperties(3, 30, true));

    listener.fetchAndStore("retry001", "https://example.com/x");

    assertThat(entity.getOgFetchStatus()).isEqualTo("RETRYABLE");
    assertThat(entity.getOgFetchAttempts()).isEqualTo(1);
  }

  @Test
  void finalFailureMarksError() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(OgMetadata.empty());

    LinkRepository repo = mock(LinkRepository.class);
    LinkEntity entity = link("retry002", "https://example.com/x");
    setField(entity, "ogFetchAttempts", 2);
    when(repo.findByShortCode("retry002")).thenReturn(Optional.of(entity));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

    LinkOgFetchListener listener =
        new LinkOgFetchListener(
            scraper,
            repo,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new NoOpCacheManager(),
            new OgFetchProperties(3, 30, true));

    listener.fetchAndStore("retry002", "https://example.com/x");

    assertThat(entity.getOgFetchStatus()).isEqualTo("ERROR");
    assertThat(entity.getOgFetchAttempts()).isEqualTo(3);
  }

  private static LinkEntity link(String shortCode, String url) {
    LinkEntity e = new LinkEntity(url, shortCode);
    setField(e, "id", 1L);
    setField(e, "createdAt", Instant.now());
    return e;
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Class<?> c = target.getClass();
      while (c != null) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          f.set(target, value);
          return;
        } catch (NoSuchFieldException ignored) {
          c = c.getSuperclass();
        }
      }
      throw new NoSuchFieldException(name);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
