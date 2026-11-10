package com.example.short_link.admin.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.application.read.LinkReportReader;
import com.example.short_link.abuse.application.read.LinkReports;
import com.example.short_link.abuse.application.read.LinkReports.ReasonCount;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.admin.application.dto.AdminLinkEvidence;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceChange;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceCreator;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceDestination;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceHistory;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidencePage;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceReason;
import com.example.short_link.admin.application.dto.AdminLinkEvidence.EvidenceReports;
import com.example.short_link.common.audit.AddressChangeHistory;
import com.example.short_link.common.audit.AddressChangeHistory.AddressChanges;
import com.example.short_link.link.application.dto.LinkEvidence;
import com.example.short_link.link.application.dto.LinkEvidence.Destination;
import com.example.short_link.link.application.dto.LinkEvidence.DestinationKind;
import com.example.short_link.link.application.read.LinkEvidenceReader;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.application.read.AccountStanding;
import com.example.short_link.user.application.read.AccountStanding.Status;
import com.example.short_link.user.application.read.AccountStandingReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminLinkEvidenceServiceTest {

  private static final ShortCode CODE = new ShortCode("suspect1");
  private static final Instant FETCHED = Instant.parse("2026-11-09T01:00:00Z");
  private static final Instant JOINED = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant MOVED = Instant.parse("2026-11-08T12:00:00Z");

  private final LinkEvidenceReader links = mock(LinkEvidenceReader.class);
  private final AccountStandingReader accounts = mock(AccountStandingReader.class);
  private final AddressChangeHistory history = mock(AddressChangeHistory.class);
  private final LinkReportReader reports = mock(LinkReportReader.class);
  private final AdminLinkEvidenceService service =
      new AdminLinkEvidenceService(links, accounts, history, reports);

  @BeforeEach
  void quietLink() {
    when(history.ofLink("suspect1")).thenReturn(new AddressChanges(180, 0, 0, null));
    when(reports.about(41L)).thenReturn(new LinkReports(0, 0, List.of()));
  }

  @Test
  void anOwnedLinkShowsItsPageItsHostsItsMovesItsMakerAndItsReports() {
    when(links.find(CODE)).thenReturn(Optional.of(link(5L, "https://cdn.example/og.png")));
    when(accounts.find(5L))
        .thenReturn(Optional.of(new AccountStanding(JOINED, Status.SUSPENDED, MOVED)));
    when(history.ofLink("suspect1"))
        .thenReturn(
            new AddressChanges(
                180,
                2,
                1,
                new AddressChanges.Latest("LINK_UPDATED", MOVED, "safe.example", "bad.example")));
    when(reports.about(41L))
        .thenReturn(new LinkReports(3, 2, List.of(new ReasonCount(AbuseReason.PHISHING, 3))));

    AdminLinkEvidence evidence = service.evidence(CODE).orElseThrow();

    assertThat(evidence.page())
        .isEqualTo(new EvidencePage("Win a prize", "Enter your card", "OK", FETCHED, true, false));
    assertThat(evidence.destinations())
        .containsExactly(
            new EvidenceDestination("bad.example", "ORIGINAL", null, null, null, null),
            new EvidenceDestination("ios.example", "BRANCH", 3, "KR", "mobile", "ios"));
    assertThat(evidence.history())
        .isEqualTo(
            new EvidenceHistory(
                180,
                2,
                1,
                new EvidenceChange("LINK_UPDATED", MOVED, "safe.example", "bad.example")));
    assertThat(evidence.creator())
        .isEqualTo(new EvidenceCreator(false, 5L, JOINED, "SUSPENDED", MOVED, 12L, 4L));
    assertThat(evidence.reports())
        .isEqualTo(new EvidenceReports(3, 2, List.of(new EvidenceReason("PHISHING", 3))));
  }

  @Test
  void aSexualReportMarksTheImageAsWithheld() {
    when(links.find(CODE)).thenReturn(Optional.of(link(null, "https://cdn.example/og.png")));
    when(reports.about(41L))
        .thenReturn(
            new LinkReports(
                2, 0, List.of(new ReasonCount(AbuseReason.SEXUAL, 1), new ReasonCount(null, 1))));

    AdminLinkEvidence evidence = service.evidence(CODE).orElseThrow();

    assertThat(evidence.page().hasImage()).isTrue();
    assertThat(evidence.page().imageWithheld()).isTrue();
    assertThat(evidence.reports().reasons())
        .containsExactly(new EvidenceReason("SEXUAL", 1), new EvidenceReason(null, 1));
  }

  @Test
  void anAnonymousLinkHasNoAccountToLookUp() {
    when(links.find(CODE)).thenReturn(Optional.of(link(null, null)));

    AdminLinkEvidence evidence = service.evidence(CODE).orElseThrow();

    assertThat(evidence.creator())
        .isEqualTo(new EvidenceCreator(true, null, null, null, null, null, null));
    assertThat(evidence.page().hasImage()).isFalse();
    assertThat(evidence.history().latest()).isNull();
    verify(accounts, never()).find(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void anOwnerWhoseAccountRowIsGoneStillShowsTheLinkCounts() {
    when(links.find(CODE)).thenReturn(Optional.of(link(5L, null)));
    when(accounts.find(5L)).thenReturn(Optional.empty());

    assertThat(service.evidence(CODE).orElseThrow().creator())
        .isEqualTo(new EvidenceCreator(false, 5L, null, null, null, 12L, 4L));
  }

  @Test
  void anUnknownCodeHasNoEvidence() {
    when(links.find(CODE)).thenReturn(Optional.empty());

    assertThat(service.evidence(CODE)).isEmpty();
  }

  private static LinkEvidence link(Long ownerId, String image) {
    return new LinkEvidence(
        41L,
        ownerId,
        new LinkEvidence.Introduction("Win a prize", "Enter your card", "OK", FETCHED, image),
        List.of(
            new Destination("bad.example", DestinationKind.ORIGINAL, null, null, null, null),
            new Destination("ios.example", DestinationKind.BRANCH, 3, "KR", "mobile", "ios")),
        ownerId == null ? null : new LinkEvidence.OwnerLinks(12, 4));
  }
}
