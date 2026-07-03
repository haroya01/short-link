package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AbuseReportRepository {

  AbuseReportEntity save(AbuseReportEntity report);

  Optional<AbuseReportEntity> findById(Long id);

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  /**
   * Snapshots of the POST subjects behind a batch of reports — title / slug / status / author
   * handle — joined in one query so the moderation queue can enrich rows without an N+1. Ids with
   * no matching post (hard-deleted) are simply absent from the result.
   */
  List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds);

  interface PostSubjectSnapshot {
    Long getSubjectId();

    String getTitle();

    String getSlug();

    String getStatus();

    String getAuthorHandle();
  }
}
