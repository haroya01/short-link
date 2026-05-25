package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.link.profile_binding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class LinkCreationServiceCollisionTest {

  @Test
  void throwsAfterMaxAttemptsAllCollide() {
    LinkRepository repository = mock(LinkRepository.class);
    ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
    when(generator.generate()).thenReturn("abc1234");
    when(repository.save(any(LinkEntity.class)))
        .thenThrow(new DataIntegrityViolationException("unique"));

    UrlSafetyChecker safetyChecker = mock(UrlSafetyChecker.class);
    when(safetyChecker.isSafe(any())).thenReturn(true);
    var blockedDomain = mock(com.example.short_link.admin.application.BlockedDomainService.class);
    when(blockedDomain.isBlocked(any())).thenReturn(false);
    CreateLinkUseCase service =
        new CreateLinkUseCase(
            repository,
            mock(LinkOgMetadataRepository.class),
            mock(LinkAccessControlRepository.class),
            mock(LinkProfileBindingRepository.class),
            mock(LinkExpirationPolicyRepository.class),
            generator,
            new SimpleMeterRegistry(),
            safetyChecker,
            event -> {},
            mock(com.example.short_link.common.audit.AuditLogService.class),
            blockedDomain,
            noopTransactionManager(),
            200L);

    assertThatThrownBy(
            () -> service.execute(CreateLinkCommand.of("https://example.com", null, null, null)))
        .isInstanceOf(LinkException.class);

    verify(generator, times(5)).generate();
    verify(repository, times(5)).save(any());
  }

  static PlatformTransactionManager noopTransactionManager() {
    PlatformTransactionManager m = mock(PlatformTransactionManager.class);
    when(m.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return m;
  }
}
