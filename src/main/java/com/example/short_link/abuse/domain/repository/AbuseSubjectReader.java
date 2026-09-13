package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.Collection;
import java.util.List;

public interface AbuseSubjectReader {
  /** 여러 글을 한 번에 조회하며, 존재하지 않는 글은 결과에서 제외한다. */
  List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds);

  /** 관리자가 삭제 여부를 확인할 수 있도록 soft 삭제 댓글도 포함한다. 없는 댓글은 제외한다. */
  List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> commentIds);

  /** 존재하지 않는 사용자는 결과에서 제외한다. */
  List<UserSubjectSnapshot> findUserSubjectSnapshots(Collection<Long> userIds);

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
