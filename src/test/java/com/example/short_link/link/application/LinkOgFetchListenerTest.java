package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class LinkOgFetchListenerTest {

  @Test
  void appliesScrapedMetadataAndEvictsCacheOnSuccess() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch("https://example.com/x"))
        .thenReturn(new OgMetadata("Title", "Desc", "https://example.com/img.png"));

    LinkRepository repository = mock(LinkRepository.class);
    LinkEntity entity = link("abc1234", "https://example.com/x");
    when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(entity));
    when(repository.save(any(LinkEntity.class))).thenAnswer(i -> i.getArgument(0));

    Cache cache = mock(Cache.class);
    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("link")).thenReturn(cache);

    LinkOgFetchListener listener =
        new LinkOgFetchListener(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            cacheManager,
            new OgFetchProperties(3, 30, true));

    listener.fetchAndStore("abc1234", "https://example.com/x");

    assertThat(entity.getOgTitle()).isEqualTo("Title");
    assertThat(entity.getOgDescription()).isEqualTo("Desc");
    assertThat(entity.getOgImage()).isEqualTo("https://example.com/img.png");
    assertThat(entity.getOgFetchStatus()).isEqualTo("OK");
    assertThat(entity.getOgFetchedAt()).isNotNull();
    verify(cache).evict("abc1234");
  }

  @Test
  void marksErrorWhenScraperReturnsEmpty() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(OgMetadata.empty());

    LinkRepository repository = mock(LinkRepository.class);
    LinkEntity entity = link("zzz1234", "https://example.com/none");
    when(repository.findByShortCode("zzz1234")).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    LinkOgFetchListener listener =
        new LinkOgFetchListener(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new NoOpCacheManager(),
            new OgFetchProperties(1, 30, true));

    listener.fetchAndStore("zzz1234", "https://example.com/none");

    assertThat(entity.getOgFetchStatus()).isEqualTo("ERROR");
    assertThat(entity.getOgFetchedAt()).isNotNull();
    assertThat(entity.getOgTitle()).isNull();
  }

  @Test
  void noopWhenLinkDeletedBeforeFetch() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(new OgMetadata("t", "d", "i"));

    LinkRepository repository = mock(LinkRepository.class);
    when(repository.findByShortCode("gone1234")).thenReturn(Optional.empty());

    LinkOgFetchListener listener =
        new LinkOgFetchListener(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new NoOpCacheManager(),
            new OgFetchProperties(3, 30, true));

    listener.fetchAndStore("gone1234", "https://example.com/x");

    verify(repository).findByShortCode("gone1234");
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
