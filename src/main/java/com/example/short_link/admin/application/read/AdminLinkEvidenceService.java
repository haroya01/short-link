package com.example.short_link.admin.application.read;

import com.example.short_link.abuse.application.read.LinkReportReader;
import com.example.short_link.abuse.application.read.LinkReports;
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
import com.example.short_link.link.application.read.LinkEvidenceReader;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.application.read.AccountStanding;
import com.example.short_link.user.application.read.AccountStandingReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminLinkEvidenceService {

  private final LinkEvidenceReader links;
  private final AccountStandingReader accounts;
  private final AddressChangeHistory addressChanges;
  private final LinkReportReader reports;

  @Transactional(readOnly = true)
  public Optional<AdminLinkEvidence> evidence(ShortCode shortCode) {
    return links.find(shortCode).map(link -> evidence(shortCode, link));
  }

  private AdminLinkEvidence evidence(ShortCode shortCode, LinkEvidence link) {
    LinkReports linkReports = reports.about(link.linkId());
    return new AdminLinkEvidence(
        page(link.introduction(), AdminLinkPreviewService.imageWithheld(linkReports)),
        link.destinations().stream().map(AdminLinkEvidenceService::destination).toList(),
        history(addressChanges.ofLink(shortCode.value())),
        creator(link),
        reports(linkReports));
  }

  private static EvidencePage page(LinkEvidence.Introduction introduction, boolean withheld) {
    return new EvidencePage(
        introduction.title(),
        introduction.description(),
        introduction.fetchStatus(),
        introduction.fetchedAt(),
        introduction.imageUrl() != null,
        withheld);
  }

  private static EvidenceDestination destination(LinkEvidence.Destination destination) {
    return new EvidenceDestination(
        destination.host(),
        destination.kind().name(),
        destination.weight(),
        destination.countryCode(),
        destination.deviceClass(),
        destination.os());
  }

  private static EvidenceHistory history(AddressChanges changes) {
    AddressChanges.Latest latest = changes.latest();
    return new EvidenceHistory(
        changes.retentionDays(),
        changes.urlChanges(),
        changes.destinationChanges(),
        latest == null
            ? null
            : new EvidenceChange(latest.action(), latest.at(), latest.fromHost(), latest.toHost()));
  }

  private EvidenceCreator creator(LinkEvidence link) {
    if (link.ownerId() == null) {
      return new EvidenceCreator(true, null, null, null, null, null, null);
    }
    Optional<AccountStanding> standing = accounts.find(link.ownerId());
    return new EvidenceCreator(
        false,
        link.ownerId(),
        standing.map(AccountStanding::joinedAt).orElse(null),
        standing.map(s -> s.status().name()).orElse(null),
        standing.map(AccountStanding::suspendedUntil).orElse(null),
        link.ownerLinks().total(),
        link.ownerLinks().disabled());
  }

  private static EvidenceReports reports(LinkReports reports) {
    return new EvidenceReports(
        reports.total(),
        reports.open(),
        reports.reasons().stream()
            .map(
                count ->
                    new EvidenceReason(
                        count.reason() == null ? null : count.reason().name(), count.count()))
            .toList());
  }
}
