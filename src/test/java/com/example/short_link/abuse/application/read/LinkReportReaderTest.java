package com.example.short_link.abuse.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.application.read.LinkReports.ReasonCount;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.ReasonTally;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkReportReaderTest {

  private final AbuseReportRepository repository = mock(AbuseReportRepository.class);
  private final LinkReportReader reader = new LinkReportReader(repository);

  @Test
  void reportsAreCountedByReasonWithTheUnresolvedOnesApart() {
    when(repository.tallyBySubject(AbuseSubjectType.LINK, 9L))
        .thenReturn(
            List.of(
                tally(AbuseReason.SPAM, AbuseReportStatus.OPEN, 2),
                tally(AbuseReason.SPAM, AbuseReportStatus.REJECTED, 1),
                tally(AbuseReason.SEXUAL, AbuseReportStatus.REVIEWING, 1),
                tally(null, AbuseReportStatus.RESOLVED, 1)));

    LinkReports reports = reader.about(9L);

    assertThat(reports.total()).isEqualTo(5);
    assertThat(reports.open()).isEqualTo(3);
    assertThat(reports.reasons())
        .containsExactly(
            new ReasonCount(AbuseReason.SPAM, 3),
            new ReasonCount(AbuseReason.SEXUAL, 1),
            new ReasonCount(null, 1));
    assertThat(reports.includes(AbuseReason.SEXUAL)).isTrue();
    assertThat(reports.includes(AbuseReason.PHISHING)).isFalse();
  }

  @Test
  void aLinkNobodyReportedHasNoReasons() {
    when(repository.tallyBySubject(AbuseSubjectType.LINK, 9L)).thenReturn(List.of());

    assertThat(reader.about(9L)).isEqualTo(new LinkReports(0, 0, List.of()));
  }

  private static ReasonTally tally(AbuseReason reason, AbuseReportStatus status, long reports) {
    return new ReasonTally() {
      @Override
      public AbuseReason getReason() {
        return reason;
      }

      @Override
      public AbuseReportStatus getStatus() {
        return status;
      }

      @Override
      public Long getReports() {
        return reports;
      }
    };
  }
}
