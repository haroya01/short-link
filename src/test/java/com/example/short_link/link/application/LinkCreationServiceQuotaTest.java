package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkCreationServiceQuotaTest {

  @Test
  void throwsWhenAuthenticatedUserAtQuota() {
    LinkRepository repo = mock(LinkRepository.class);
    when(repo.countByUserId(42L)).thenReturn(200L);
    when(repo.findFirstByUserIdAndOriginalUrl(any(), any())).thenReturn(Optional.empty());

    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    LinkCreationService svc =
        new LinkCreationService(
            repo,
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            200L);

    assertThatThrownBy(() -> svc.create("https://example.com/x", 42L, null, null))
        .isInstanceOf(LinkQuotaExceededException.class);

    verify(repo, never()).save(any(LinkEntity.class));
  }

  @Test
  void anonymousIsNotQuotaChecked() {
    LinkRepository repo = mock(LinkRepository.class);
    ShortCodeGenerator gen = mock(ShortCodeGenerator.class);
    when(gen.generate()).thenReturn("anon123");
    when(repo.save(any(LinkEntity.class))).thenAnswer(i -> withId(i.getArgument(0), 1L));

    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    LinkCreationService svc =
        new LinkCreationService(
            repo,
            gen,
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            200L);

    LinkCreated created = svc.create("https://example.com", null, null, null);

    assertThat(created.shortCode()).isEqualTo("anon123");
    verify(repo, never()).countByUserId(any());
  }

  @Test
  void deduplicatesExistingActiveLinkForAuthUser() {
    LinkRepository repo = mock(LinkRepository.class);
    LinkEntity existing = withId(new LinkEntity("https://example.com/x", "exist01", 99L, null), 7L);
    when(repo.findFirstByUserIdAndOriginalUrl(99L, "https://example.com/x"))
        .thenReturn(Optional.of(existing));

    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    LinkCreationService svc =
        new LinkCreationService(
            repo,
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            200L);

    LinkCreated created = svc.create("https://example.com/x", 99L, null, null);

    assertThat(created.shortCode()).isEqualTo("exist01");
    verify(repo, never()).save(any(LinkEntity.class));
    verify(repo, never()).countByUserId(any());
  }

  @Test
  void rejectsReservedCustomCode() {
    LinkRepository repo = mock(LinkRepository.class);
    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    LinkCreationService svc =
        new LinkCreationService(
            repo,
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            200L);

    assertThatThrownBy(() -> svc.create("https://example.com", 1L, "login", null))
        .isInstanceOf(ReservedShortCodeException.class);

    verify(repo, never()).save(any(LinkEntity.class));
  }

  @Test
  void doesNotDeduplicateWhenExistingIsExpired() {
    LinkRepository repo = mock(LinkRepository.class);
    LinkEntity existing =
        withId(
            new LinkEntity(
                "https://example.com/x", "expired1", 99L, Instant.now().minusSeconds(3600)),
            8L);
    when(repo.findFirstByUserIdAndOriginalUrl(99L, "https://example.com/x"))
        .thenReturn(Optional.of(existing));
    when(repo.countByUserId(99L)).thenReturn(0L);

    ShortCodeGenerator gen = mock(ShortCodeGenerator.class);
    when(gen.generate()).thenReturn("newcode");
    when(repo.save(any(LinkEntity.class))).thenAnswer(i -> withId(i.getArgument(0), 9L));

    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    LinkCreationService svc =
        new LinkCreationService(
            repo,
            gen,
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            200L);

    LinkCreated created = svc.create("https://example.com/x", 99L, null, null);
    assertThat(created.shortCode()).isEqualTo("newcode");
  }

  private static LinkEntity withId(LinkEntity entity, Long id) {
    try {
      Field f = LinkEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return entity;
  }
}
