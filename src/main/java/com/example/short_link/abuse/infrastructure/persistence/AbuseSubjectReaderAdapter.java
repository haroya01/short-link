package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AbuseSubjectReaderAdapter implements AbuseSubjectReader {
  private final JpaAbuseSubjectQueries jpa;

  @Override
  public List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds) {
    return jpa.findPostSubjectSnapshots(postIds);
  }

  @Override
  public List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> commentIds) {
    return jpa.findCommentSubjectSnapshots(commentIds);
  }

  @Override
  public List<UserSubjectSnapshot> findUserSubjectSnapshots(Collection<Long> userIds) {
    return jpa.findUserSubjectSnapshots(userIds);
  }

  @Override
  public boolean subjectExists(AbuseSubjectType subjectType, Long subjectId) {
    if (subjectId == null) {
      return false;
    }
    return switch (subjectType) {
      case POST -> jpa.countPostById(subjectId) > 0;
      case COMMENT -> jpa.countCommentById(subjectId) > 0;
      case USER -> jpa.countUserById(subjectId) > 0;
    };
  }
}
