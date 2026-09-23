package com.example.short_link.link.og.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class LinkOgFetchServiceTest {

  @Test
  void appliesScrapedMetadataAndEvictsCacheOnSuccess() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch("https://example.com/x"))
        .thenReturn(new OgMetadata("Title", "Desc", "https://example.com/img.png"));

    LinkRepository repository = mock(LinkRepository.class);
    LinkEntity entity = link("abc1234", "https://example.com/x");
    when(repository.findByShortCode(new ShortCode("abc1234"))).thenReturn(Optional.of(entity));

    Cache cache = mock(Cache.class);
    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("link")).thenReturn(cache);

    LinkOgFetchService listener =
        new LinkOgFetchService(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new LinkCacheEviction(cacheManager),
            new OgFetchProperties(3, 30, true));

    listener.fetchAfterCommit(new ShortCode("abc1234"), "https://example.com/x");

    verify(repository)
        .recordOgFetched(
            eq(entity.getId()),
            eq("Title"),
            eq("Desc"),
            eq("https://example.com/img.png"),
            any(Instant.class));
    verify(repository, never()).save(any());
    verify(cache).evictIfPresent(new ShortCode("abc1234"));
  }

  @Test
  void marksErrorWhenScraperReturnsEmpty() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(OgMetadata.empty());

    LinkRepository repository = mock(LinkRepository.class);
    LinkEntity entity = link("zzz1234", "https://example.com/none");
    when(repository.findByShortCode(new ShortCode("zzz1234"))).thenReturn(Optional.of(entity));

    LinkOgFetchService listener =
        new LinkOgFetchService(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new LinkCacheEviction(new NoOpCacheManager()),
            new OgFetchProperties(1, 30, true));

    listener.fetchAfterCommit(new ShortCode("zzz1234"), "https://example.com/none");

    verify(repository).recordOgFetchFailed(eq(entity.getId()), any(Instant.class), eq(false));
    verify(repository, never()).recordOgFetched(any(), any(), any(), any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void noopWhenLinkDeletedBeforeFetch() {
    OgScraper scraper = mock(OgScraper.class);
    when(scraper.fetch(any())).thenReturn(new OgMetadata("t", "d", "i"));

    LinkRepository repository = mock(LinkRepository.class);
    when(repository.findByShortCode(new ShortCode("gone1234"))).thenReturn(Optional.empty());

    LinkOgFetchService listener =
        new LinkOgFetchService(
            scraper,
            repository,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            new LinkCacheEviction(new NoOpCacheManager()),
            new OgFetchProperties(3, 30, true));

    listener.fetchAfterCommit(new ShortCode("gone1234"), "https://example.com/x");

    verify(repository).findByShortCode(new ShortCode("gone1234"));
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
