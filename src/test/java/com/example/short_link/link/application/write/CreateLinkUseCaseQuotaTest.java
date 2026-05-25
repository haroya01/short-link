package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.link.profile_binding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class LinkCreationServiceQuotaTest {

  @Test
  void throwsWhenAuthenticatedUserAtQuota() {
    LinkRepository repo = mock(LinkRepository.class);
    when(repo.countByUserId(42L)).thenReturn(200L);
    when(repo.findFirstByUserIdAndOriginalUrl(any(), any())).thenReturn(Optional.empty());

    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    CreateLinkUseCase svc =
        new CreateLinkUseCase(
            repo,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            mockBlockedDomainService(),
            noopTx(),
            200L);

    assertThatThrownBy(
            () -> svc.execute(CreateLinkCommand.of("https://example.com/x", 42L, null, null)))
        .isInstanceOf(LinkException.class);

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

    CreateLinkUseCase svc =
        new CreateLinkUseCase(
            repo,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            gen,
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            mockBlockedDomainService(),
            noopTx(),
            200L);

    LinkCreated created =
        svc.execute(CreateLinkCommand.of("https://example.com", null, null, null));

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

    CreateLinkUseCase svc =
        new CreateLinkUseCase(
            repo,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            mockBlockedDomainService(),
            noopTx(),
            200L);

    LinkCreated created =
        svc.execute(CreateLinkCommand.of("https://example.com/x", 99L, null, null));

    assertThat(created.shortCode()).isEqualTo("exist01");
    verify(repo, never()).save(any(LinkEntity.class));
    verify(repo, never()).countByUserId(any());
  }

  @Test
  void rejectsReservedCustomCode() {
    LinkRepository repo = mock(LinkRepository.class);
    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(any())).thenReturn(true);

    CreateLinkUseCase svc =
        new CreateLinkUseCase(
            repo,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            mock(ShortCodeGenerator.class),
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            mockBlockedDomainService(),
            noopTx(),
            200L);

    assertThatThrownBy(
            () -> svc.execute(CreateLinkCommand.of("https://example.com", 1L, "login", null)))
        .isInstanceOf(LinkException.class);

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

    CreateLinkUseCase svc =
        new CreateLinkUseCase(
            repo,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            gen,
            new SimpleMeterRegistry(),
            safety,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            mockBlockedDomainService(),
            noopTx(),
            200L);

    LinkCreated created =
        svc.execute(CreateLinkCommand.of("https://example.com/x", 99L, null, null));
    assertThat(created.shortCode()).isEqualTo("newcode");
  }

  private static com.example.short_link.admin.application.BlockedDomainService
      mockBlockedDomainService() {
    var m = mock(com.example.short_link.admin.application.BlockedDomainService.class);
    when(m.isBlocked(any())).thenReturn(false);
    return m;
  }

  private static PlatformTransactionManager noopTx() {
    PlatformTransactionManager m = mock(PlatformTransactionManager.class);
    when(m.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return m;
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
