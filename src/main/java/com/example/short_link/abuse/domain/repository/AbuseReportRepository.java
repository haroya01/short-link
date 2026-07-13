package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AbuseReportRepository {

  AbuseReportEntity save(AbuseReportEntity report);

  Optional<AbuseReportEntity> findById(Long id);

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  /**
   * 같은 신고자가 같은 대상에 대해 아직 열린(OPEN/REVIEWING) 신고를 갖고 있는지 — 중복 신고 가드용. 익명 신고 (reporterUserId=null)는 대상
   * 못하므로 가드 밖.
   */
  boolean existsOpenReport(Long reporterUserId, AbuseSubjectType subjectType, Long subjectId);

  /**
   * Snapshots of the POST subjects behind a batch of reports — title / slug / status / author
   * handle — joined in one query so the moderation queue can enrich rows without an N+1. Ids with
   * no matching post (hard-deleted) are simply absent from the result.
   */
  List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds);

  /**
   * COMMENT 대상 스냅샷 — 본문 발췌(앞 200자)와 작성자 핸들을 한 쿼리로. soft 삭제된 댓글도 관리자 큐에는 보여야 하므로 deleted_at 로 거르지
   * 않는다(뷰의 removed 플래그로 표시). 없는 댓글은 결과에서 빠진다.
   */
  List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> commentIds);

  /** USER 대상 스냅샷 — 핸들/제재상태를 한 쿼리로. 없는 유저는 결과에서 빠진다. */
  List<UserSubjectSnapshot> findUserSubjectSnapshots(Collection<Long> userIds);

  /** 단일 대상 존재검사 — 없는 대상 신고를 제출 시점에 거부하기 위함. */
  boolean subjectExists(AbuseSubjectType subjectType, Long subjectId);

  interface PostSubjectSnapshot {
    Long getSubjectId();

    String getTitle();

    String getSlug();

    String getStatus();

    String getAuthorHandle();
  }

  interface CommentSubjectSnapshot {
    Long getSubjectId();

    String getExcerpt();

    String getAuthorHandle();

    /** deleted_at 이 채워졌는지 — soft 삭제된 댓글이면 뷰가 removed 로 표시. */
    Long getDeleted();
  }

  interface UserSubjectSnapshot {
    Long getSubjectId();

    String getHandle();

    String getModerationStatus();
  }
}
