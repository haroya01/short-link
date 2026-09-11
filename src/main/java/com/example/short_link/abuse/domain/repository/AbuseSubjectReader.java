package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.Collection;
import java.util.List;

/** 신고 대상의 존재와 관리자 화면에 필요한 현재 스냅샷을 조회한다. */
public interface AbuseSubjectReader {
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
