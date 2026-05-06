package com.example.short_link.link.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  long countByLinkId(Long linkId);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS cnt FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

  @Query(
      "SELECT FUNCTION('DATE', c.clickedAt) AS day, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId "
          + "AND c.clickedAt >= :from "
          + "GROUP BY FUNCTION('DATE', c.clickedAt) ORDER BY day")
  List<DailyClickRow> findDailyClicks(@Param("linkId") Long linkId, @Param("from") Instant from);

  @Query(
      "SELECT c.referrer AS referrer, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId "
          + "GROUP BY c.referrer ORDER BY cnt DESC")
  List<ReferrerClickRow> findReferrerClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.userAgent AS userAgent, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId "
          + "GROUP BY c.userAgent ORDER BY cnt DESC")
  List<UserAgentClickRow> findUserAgentClicks(@Param("linkId") Long linkId);

  interface LinkClickCount {
    Long getLinkId();

    Long getCnt();
  }

  interface DailyClickRow {
    java.sql.Date getDay();

    Long getCnt();
  }

  interface ReferrerClickRow {
    String getReferrer();

    Long getCnt();
  }

  interface UserAgentClickRow {
    String getUserAgent();

    Long getCnt();
  }
}
