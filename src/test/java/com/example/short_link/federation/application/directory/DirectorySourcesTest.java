package com.example.short_link.federation.application.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectorySourcesTest {

  @Mock private FederationDirectorySourceRepository sources;
  @Mock private FederationDomainBlockRepository serverBlocks;

  @BeforeEach
  void savesWhatItIsGiven() {
    lenient().when(sources.save(any())).thenAnswer(call -> call.getArgument(0));
    lenient().when(serverBlocks.severityFor(any())).thenReturn(Optional.empty());
  }

  private DirectorySources directories(boolean enabled) {
    return new DirectorySources(
        sources,
        serverBlocks,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        new DirectoryProperties(enabled, 100, 1_000, null, null, null, null));
  }

  private static FederationErrorCode codeOf(Runnable call) {
    try {
      call.run();
    } catch (FederationException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a FederationException");
  }

  @Test
  void anInstanceIsAddedByItsDomainWithTheDefaultCapAndChangedInPlace() {
    when(sources.findById("qdon.space")).thenReturn(Optional.empty());

    DirectorySources.View added = directories(true).put(" @QDON.space ", true, null);

    assertThat(added.host()).isEqualTo("qdon.space");
    assertThat(added.enabled()).isTrue();
    assertThat(added.cap()).isEqualTo(1_000);

    FederationDirectorySourceEntity existing =
        new FederationDirectorySourceEntity("qdon.space", true, 1_000);
    existing.failed("HTTP 503", Instant.parse("2026-10-17T06:00:00Z"));
    when(sources.findById("qdon.space")).thenReturn(Optional.of(existing));
    DirectorySources.View changed = directories(true).put("qdon.space", false, 400);
    assertThat(changed.enabled()).isFalse();
    assertThat(changed.cap()).isEqualTo(400);
    assertThat(changed.retryAfter()).isNull();
    assertThat(changed.failures()).isZero();
  }

  @Test
  void noInstanceIsTakenWhileDirectoriesAreOffNorThisServerNorABlockedOne() {
    assertThat(codeOf(() -> directories(false).put("qdon.space", true, null)))
        .isEqualTo(FederationErrorCode.DIRECTORY_DISABLED);
    assertThat(codeOf(() -> directories(true).put("kurl.me", true, null)))
        .isEqualTo(FederationErrorCode.REMOTE_DOMAIN_INVALID);
    assertThat(codeOf(() -> directories(true).put("not a host", true, null)))
        .isEqualTo(FederationErrorCode.REMOTE_DOMAIN_INVALID);
    when(serverBlocks.severityFor("bad.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    assertThat(codeOf(() -> directories(true).put("bad.example", true, null)))
        .isEqualTo(FederationErrorCode.DIRECTORY_SOURCE_BLOCKED);
    verify(sources, never()).save(any());
  }

  @Test
  void removingAnInstanceNoOneAddedIsNotFound() {
    when(sources.findById("qdon.space")).thenReturn(Optional.empty());

    assertThat(codeOf(() -> directories(true).delete("qdon.space")))
        .isEqualTo(FederationErrorCode.DIRECTORY_SOURCE_NOT_FOUND);
  }

  @Test
  void theDefaultsAreAHundredNewAccountsARunAThousandReadAndThreeDaysOfConsent() {
    DirectoryProperties defaults = new DirectoryProperties(false, 0, 0, null, null, null, null);

    assertThat(defaults.newPerRun()).isEqualTo(100);
    assertThat(defaults.defaultCap()).isEqualTo(1_000);
    assertThat(defaults.consent()).isEqualTo(Duration.ofDays(3));
    assertThat(defaults.pageGap()).isEqualTo(Duration.ofSeconds(12));
    assertThat(defaults.fetchGap()).isEqualTo(Duration.ofSeconds(3));
  }
}
