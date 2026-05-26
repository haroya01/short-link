package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Lifetime totals and timestamps for click_event. Single-link counts (human / bot / unique /
 * preview / direct / profile-channel / since / datacenter) plus first/last click timestamps, and
 * multi-link count variants used by campaign and my-links aggregates.
 */
public interface ClickTotalsReadRepository extends Repository<ClickEventEntity, Long> {

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false")
  long countHumanByLinkId(@Param("linkId") Long linkId);

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = true")
  long countBotByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.referrer IS NULL")
  long countDirectByLinkId(@Param("linkId") Long linkId);

  /**
   * Clicks attributed to "this link was clicked from the owner's public profile page". Every LINK
   * block on /u/&lt;handle&gt; appends {@code ?src=profile-<handle>} when sending visitors to the
   * short URL, so we count any {@code sourceChannel} starting with the {@code profile-} prefix. The
   * owner-facing stats page surfaces this as a top-level KPI so they can split "from my profile"
   * from external sharing without scrolling to the channel breakdown.
   */
  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.sourceChannel LIKE 'profile-%'")
  long countProfileChannelByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(DISTINCT c.visitorHash) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.visitorHash IS NOT NULL")
  long countUniqueVisitorsByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT MIN(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false")
  Instant findFirstClickAt(@Param("linkId") Long linkId);

  @Query(
      "SELECT MAX(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false")
  Instant findLastClickAt(@Param("linkId") Long linkId);

  /**
   * OG preview hits are persisted with bot=true and a "preview:..." prefixed bot_name (set by
   * {@code ClickRecorder.recordPreview}) so the stats UI can split social/messenger previews out of
   * real bot traffic regardless of yauaa coverage.
   */
  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = true AND c.botName LIKE 'preview:%'")
  long countPreviewByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.clickedAt >= :since")
  long countSinceByLinkId(@Param("linkId") Long linkId, @Param("since") Instant since);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.botName LIKE 'datacenter:%'")
  long countDatacenterClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt >= :since "
          + "GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("since") Instant since);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt < :before "
          + "GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIdsBefore(
      @Param("ids") List<Long> ids, @Param("before") Instant before);

  @Query(
      "SELECT MAX(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt < :before")
  Instant findLastClickBeforeByLinkIds(
      @Param("ids") List<Long> ids, @Param("before") Instant before);
}
