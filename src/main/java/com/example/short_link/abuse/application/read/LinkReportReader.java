package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.application.read.LinkReports.ReasonCount;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.ReasonTally;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LinkReportReader {

  private static final Set<AbuseReportStatus> UNRESOLVED =
      Set.of(AbuseReportStatus.OPEN, AbuseReportStatus.REVIEWING);

  private final AbuseReportRepository reports;

  @Transactional(readOnly = true)
  public LinkReports about(long linkId) {
    long total = 0;
    long open = 0;
    Map<AbuseReason, Long> byReason = new LinkedHashMap<>();
    for (ReasonTally tally : reports.tallyBySubject(AbuseSubjectType.LINK, linkId)) {
      total += tally.getReports();
      if (UNRESOLVED.contains(tally.getStatus())) {
        open += tally.getReports();
      }
      byReason.merge(tally.getReason(), tally.getReports(), Long::sum);
    }
    List<ReasonCount> reasons = new ArrayList<>();
    byReason.forEach((reason, count) -> reasons.add(new ReasonCount(reason, count)));
    reasons.sort(
        Comparator.comparingLong(ReasonCount::count)
            .reversed()
            .thenComparing(ReasonCount::reason, Comparator.nullsLast(Comparator.naturalOrder())));
    return new LinkReports(total, open, reasons);
  }
}
