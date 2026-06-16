package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostHighlightReplyRepository
    extends JpaRepository<PostHighlightReplyEntity, Long> {

  List<PostHighlightReplyEntity> findAllByHighlightIdOrderByCreatedAtAsc(Long highlightId);

  @Modifying
  @Query("delete from PostHighlightReplyEntity r where r.highlightId = :highlightId")
  int deleteAllByHighlightId(@Param("highlightId") Long highlightId);

  @Query(
      "SELECT r.highlightId AS highlightId, COUNT(r) AS cnt FROM PostHighlightReplyEntity r "
          + "WHERE r.highlightId IN :highlightIds GROUP BY r.highlightId")
  List<HighlightReplyCount> countGroupedByHighlightId(
      @Param("highlightIds") Collection<Long> highlightIds);

  interface HighlightReplyCount {
    Long getHighlightId();

    Long getCnt();
  }
}
