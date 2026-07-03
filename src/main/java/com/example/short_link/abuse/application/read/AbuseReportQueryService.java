package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.PostSubjectSnapshot;
import com.example.short_link.common.web.PostPublicUrlBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbuseReportQueryService {

  private static final String UNPUBLISHED = "UNPUBLISHED";

  private final AbuseReportRepository abuseReportRepository;
  private final PostPublicUrlBuilder postPublicUrlBuilder;

  public List<AbuseReportView> listAll() {
    return enrichAll(abuseReportRepository.findAllByOrderByCreatedAtDesc());
  }

  public List<AbuseReportView> listByStatus(AbuseReportStatus status) {
    return enrichAll(abuseReportRepository.findAllByStatusOrderByCreatedAtDesc(status));
  }

  /** Enrich a single report — used by the resolve endpoint so its response keeps the snapshot. */
  public AbuseReportView enrich(AbuseReportEntity report) {
    return enrichAll(List.of(report)).get(0);
  }

  private List<AbuseReportView> enrichAll(List<AbuseReportEntity> reports) {
    List<Long> postIds =
        reports.stream()
            .filter(r -> r.getSubjectType() == AbuseSubjectType.POST)
            .map(AbuseReportEntity::getSubjectId)
            .distinct()
            .toList();
    Map<Long, PostSubjectSnapshot> byId =
        postIds.isEmpty()
            ? Map.of()
            : abuseReportRepository.findPostSubjectSnapshots(postIds).stream()
                .collect(Collectors.toMap(PostSubjectSnapshot::getSubjectId, Function.identity()));
    return reports.stream().map(report -> toView(report, byId)).toList();
  }

  private AbuseReportView toView(AbuseReportEntity report, Map<Long, PostSubjectSnapshot> byId) {
    // A USER/COMMENT subjectId could numerically collide with a post id, so only POST reports look
    // up a snapshot.
    PostSubjectSnapshot snapshot =
        report.getSubjectType() == AbuseSubjectType.POST ? byId.get(report.getSubjectId()) : null;
    if (snapshot == null) {
      return AbuseReportView.from(report);
    }
    String url = postPublicUrlBuilder.build(snapshot.getAuthorHandle(), snapshot.getSlug());
    boolean removed = UNPUBLISHED.equals(snapshot.getStatus());
    return AbuseReportView.enriched(
        report, snapshot.getTitle(), snapshot.getAuthorHandle(), url, removed);
  }
}
