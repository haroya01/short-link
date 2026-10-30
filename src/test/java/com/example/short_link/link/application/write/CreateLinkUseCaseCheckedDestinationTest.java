package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.infrastructure.persistence.LinkSidecarPersister;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CreateLinkUseCaseCheckedDestinationTest {

  private final LinkRepository repository = mock(LinkRepository.class);
  private final ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
  private final UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
  private final BlockedDomainChecker blockedDomains = mock(BlockedDomainChecker.class);
  private CreateLinkUseCase links;

  @BeforeEach
  void aLinkServiceForTheKurlSite() {
    when(generator.generate()).thenReturn("chk0001");
    when(repository.save(any(LinkEntity.class))).thenAnswer(call -> call.getArgument(0));
    links =
        new CreateLinkUseCase(
            repository,
            generator,
            new SimpleMeterRegistry(),
            event -> {},
            mock(AuditLogService.class),
            new CreateLinkValidator(
                blockedDomains,
                safety,
                new SimpleMeterRegistry(),
                "https://kurl.me",
                "https://kurl.me"),
            new LinkSidecarPersister(mock(EntityManager.class)),
            linkId -> false,
            new BCryptPasswordEncoder(4),
            LinkCreationServiceCollisionTest.noopTransactionManager(),
            200L);
  }

  @Test
  void aCheckedDestinationIsStoredWithoutAskingAgain() {
    when(safety.isSafe("https://example.com/a")).thenReturn(true);
    CreateLinkCommand command = CreateLinkCommand.of("https://example.com/a", 7L, null, null);

    CheckedDestination destination = links.check(command);
    links.execute(command, destination);

    verify(safety).isSafe("https://example.com/a");
    verify(repository).save(any(LinkEntity.class));
  }

  @Test
  void aDestinationCheckedForOneUrlCannotStoreAnother() {
    when(safety.isSafe(any())).thenReturn(true);
    CheckedDestination destination =
        links.check(CreateLinkCommand.of("https://example.com/a", 7L, null, null));

    assertThatThrownBy(
            () ->
                links.execute(
                    CreateLinkCommand.of("https://example.com/b", 7L, null, null), destination))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void anUnsafeDestinationIsRefusedAtTheCheck() {
    when(safety.isSafe("https://bad.example/x")).thenReturn(false);

    assertThatThrownBy(
            () -> links.check(CreateLinkCommand.of("https://bad.example/x", 7L, null, null)))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.MALICIOUS_URL));
  }

  @Test
  void anOwnPageIsLinkedWithoutAskingSafeBrowsingOrTheBlockList() {
    links.createForOwnPage("https://kurl.me/e/party", 7L);

    verify(safety, never()).isSafe(any());
    verify(blockedDomains, never()).isBlocked(any());
    verify(repository).save(any(LinkEntity.class));
  }

  @Test
  void aForeignUrlCannotPassAsAnOwnPage() {
    assertThatThrownBy(() -> links.createForOwnPage("https://kurl.me.evil.com/e/party", 7L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> links.createForOwnPage("https://example.com/e/party", 7L))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).save(any());
  }
}
