package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.Collection;
import java.util.List;

public interface AbuseSubjectReader {
  List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds);

  // 관리자가 삭제 여부를 확인할 수 있게 soft 삭제된 댓글도 포함한다.
  List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> commentIds);

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

    Long getDeleted();
  }

  interface UserSubjectSnapshot {
    Long getSubjectId();

    String getHandle();

    String getModerationStatus();
  }
}
