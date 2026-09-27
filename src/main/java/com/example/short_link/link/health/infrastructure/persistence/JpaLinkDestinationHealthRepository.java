package com.example.short_link.link.health.infrastructure.persistence;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.health.domain.LinkDestinationHealthEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkDestinationHealthRepository
    extends JpaRepository<LinkDestinationHealthEntity, Long> {

  @Query(
      """
      SELECT l.id AS linkId, l.shortCode AS shortCode, l.userId AS userId,
        l.originalUrl AS originalUrl, l.note AS note
      FROM LinkEntity l
      LEFT JOIN LinkDestinationHealthEntity h ON h.linkId = l.id
      WHERE l.userId IS NOT NULL
        AND (l.expiresAt IS NULL OR l.expiresAt > :now)
        AND (h.checkedAt IS NULL OR h.checkedAt < :checkedBefore OR h.checkedUrl <> l.originalUrl)
      ORDER BY CASE WHEN h.checkedUrl <> l.originalUrl THEN 0 ELSE 1 END, h.checkedAt ASC, l.id DESC
      """)
  List<DueRow> findDue(
      @Param("now") Instant now, @Param("checkedBefore") Instant checkedBefore, Pageable page);

  interface DueRow {
    Long getLinkId();

    ShortCode getShortCode();

    Long getUserId();

    String getOriginalUrl();

    String getNote();
  }
}
