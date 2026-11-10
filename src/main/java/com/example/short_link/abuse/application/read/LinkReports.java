package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReason;
import java.util.List;

public record LinkReports(long total, long open, List<ReasonCount> reasons) {

  public LinkReports {
    reasons = List.copyOf(reasons);
  }

  public boolean includes(AbuseReason reason) {
    return reasons.stream().anyMatch(count -> count.reason() == reason);
  }

  public record ReasonCount(AbuseReason reason, long count) {}
}
