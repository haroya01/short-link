package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerBlockImportTest {

  private static final String HEADER =
      "#domain,#severity,#reject_media,#reject_reports,#public_comment,#obfuscate\n";

  @Mock private ServerBlocks serverBlocks;

  private ServerBlockImport importer() {
    return new ServerBlockImport(
        serverBlocks,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")));
  }

  private static ServerBlocks.View blocked(String domain, ServerBlockSeverity severity) {
    return new ServerBlocks.View(domain, severity, "ours", Instant.EPOCH);
  }

  @Test
  void suspendAndSilenceBecomeSuspendAndLimitAndOtherSeveritiesAreSkipped() {
    when(serverBlocks.list()).thenReturn(List.of());

    ServerBlockImport.Imported imported =
        importer()
            .importList(
                HEADER
                    + "Bad.Example,suspend,true,true,\"csam, spam\",false\n"
                    + "noisy.example,Silence,true,false,,false\n"
                    + "watch.example,noop,false,false,,false\n",
                false);

    assertThat(imported).isEqualTo(new ServerBlockImport.Imported(2, 0, 1, List.of()));
    verify(serverBlocks).block("bad.example", ServerBlockSeverity.SUSPEND, "csam, spam");
    verify(serverBlocks).block("noisy.example", ServerBlockSeverity.LIMIT, "");
  }

  @Test
  void aServerAlreadyBlockedHereKeepsTheModeratorsDecisionAndARepeatIsCountedOnce() {
    when(serverBlocks.list())
        .thenReturn(List.of(blocked("bad.example", ServerBlockSeverity.LIMIT)));

    ServerBlockImport.Imported imported =
        importer()
            .importList(
                HEADER
                    + "BAD.example,suspend,true,true,,false\n"
                    + "new.example,suspend,true,true,,false\n"
                    + "@New.Example,silence,true,true,,false\n",
                false);

    assertThat(imported).isEqualTo(new ServerBlockImport.Imported(1, 2, 0, List.of()));
    verify(serverBlocks).list();
    verify(serverBlocks).block("new.example", ServerBlockSeverity.SUSPEND, "");
    verifyNoMoreInteractions(serverBlocks);
  }

  @Test
  void wildcardsObfuscatedNamesAndThisServerAreListedAsInvalid() {
    when(serverBlocks.list()).thenReturn(List.of());

    ServerBlockImport.Imported imported =
        importer()
            .importList(
                HEADER
                    + "*.wild.example,suspend,,,,\n"
                    + "b*d.example,suspend,,,,true\n"
                    + "kurl.me,suspend,,,,\n",
                false);

    assertThat(imported)
        .isEqualTo(
            new ServerBlockImport.Imported(
                0, 0, 3, List.of("*.wild.example", "b*d.example", "kurl.me")));
    verify(serverBlocks, never()).block(anyString(), any(), any());
  }

  @Test
  void aDryRunCountsTheSameWithoutBlockingAnything() {
    when(serverBlocks.list())
        .thenReturn(List.of(blocked("kept.example", ServerBlockSeverity.SUSPEND)));

    ServerBlockImport.Imported imported =
        importer()
            .importList(
                "#severity,#domain\nsuspend,kept.example\nsilence,new.example\nnoop,x.example\n",
                true);

    assertThat(imported).isEqualTo(new ServerBlockImport.Imported(1, 1, 1, List.of()));
    verify(serverBlocks, never()).block(anyString(), any(), any());
  }

  @Test
  void aReasonLongerThanTheBlockKeepsIsCut() {
    when(serverBlocks.list()).thenReturn(List.of());

    importer().importList(HEADER + "long.example,suspend,,,\"" + "x".repeat(600) + "\",\n", false);

    verify(serverBlocks).block("long.example", ServerBlockSeverity.SUSPEND, "x".repeat(500));
  }

  @Test
  void aListWithoutDomainAndSeverityColumnsOrWithTooManyServersIsRefused() {
    StringBuilder tooMany = new StringBuilder(HEADER);
    for (int i = 0; i <= ServerBlockImport.MAX_SERVERS; i++) {
      tooMany.append("s").append(i).append(".example,suspend,,,,\n");
    }
    for (String bad :
        new String[] {
          "", "bad.example\nworse.example\n", "#domain\nbad.example\n", tooMany.toString()
        }) {
      assertThatThrownBy(() -> importer().importList(bad, false))
          .isInstanceOfSatisfying(
              FederationException.class,
              e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.SERVER_LIST_INVALID));
    }
    verifyNoMoreInteractions(serverBlocks);
  }
}
