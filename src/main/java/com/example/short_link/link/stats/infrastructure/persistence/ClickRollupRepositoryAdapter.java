package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ClickRollupRepositoryAdapter implements ClickRollupRepository {

  private static final String HOURLY =
      """
      INSERT INTO click_hourly
        (link_id, hour_start, age_day, humans, bots, first_human_at, last_human_at, last_at)
      SELECT c.link_id,
        FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(c.clicked_at) / 3600) * 3600) AS hour_start,
        COALESCE(TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at), -1) AS age_day,
        SUM(c.is_bot = 0), SUM(c.is_bot = 1),
        FROM_UNIXTIME(MIN(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END)),
        FROM_UNIXTIME(MAX(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END)),
        MAX(c.clicked_at)
      FROM click_event c LEFT JOIN link l ON l.id = c.link_id
      WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)
      GROUP BY c.link_id, hour_start, age_day
      """;

  private record Dimension(int code, String value, String num, boolean keepNull, boolean bots) {

    String insert() {
      String v = value == null ? "NULL" : value;
      String n = num == null ? "NULL" : num;
      String key =
          "UNHEX(MD5(CONCAT(IF("
              + v
              + " IS NULL, '-', CONCAT('+', "
              + v
              + ")), '|', IFNULL("
              + n
              + ", '-'))))";
      String present = value != null ? value : num;
      String groupBy =
          value != null && num != null ? value + ", " + num : value != null ? value : num;
      return "INSERT INTO click_daily_dim "
          + "(link_id, dim, day_start, is_bot, value_key, value, num, clicks, first_at) "
          + "SELECT c.link_id, "
          + code
          + ", FROM_UNIXTIME(:dayStart), c.is_bot, "
          + key
          + ", "
          + (value == null ? "NULL" : "LEFT(" + value + ", 2048)")
          + ", "
          + n
          + ", COUNT(*), MIN(c.clicked_at) FROM click_event c "
          + "WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)"
          + (bots ? "" : " AND c.is_bot = 0")
          + (keepNull ? "" : " AND " + present + " IS NOT NULL")
          + " GROUP BY c.link_id, c.is_bot, "
          + groupBy;
    }
  }

  private static final List<String> DIMENSIONS =
      List.of(
              new Dimension(ClickRollup.REFERRER, "c.referrer", null, true, false),
              new Dimension(ClickRollup.REFERRER_HOST, "c.referrer_host", null, false, false),
              new Dimension(ClickRollup.DEVICE, "c.device_class", null, true, false),
              new Dimension(ClickRollup.OS, "c.os_name", null, true, false),
              new Dimension(ClickRollup.BROWSER, "c.browser_name", null, true, false),
              new Dimension(ClickRollup.BOT_NAME, "c.bot_name", null, true, true),
              new Dimension(ClickRollup.UTM_CAMPAIGN, "c.utm_campaign", null, false, false),
              new Dimension(ClickRollup.UTM_SOURCE, "c.utm_source", null, false, false),
              new Dimension(ClickRollup.UTM_MEDIUM, "c.utm_medium", null, false, false),
              new Dimension(ClickRollup.UTM_CONTENT, "c.utm_content", null, false, false),
              new Dimension(ClickRollup.UTM_TERM, "c.utm_term", null, false, false),
              new Dimension(ClickRollup.CLIENT_APP, "c.client_app", null, true, false),
              new Dimension(ClickRollup.FETCH_SITE, "c.fetch_site", null, false, false),
              new Dimension(ClickRollup.POST, null, "c.post_id", false, false),
              new Dimension(ClickRollup.SOURCE_CHANNEL, "c.source_channel", null, false, false),
              new Dimension(ClickRollup.DESTINATION, null, "c.destination_id", true, false),
              new Dimension(ClickRollup.COUNTRY, "c.country_code", null, true, false),
              new Dimension(ClickRollup.REGION, "c.region_name", null, false, false),
              new Dimension(ClickRollup.CITY, "c.city_name", null, false, false),
              new Dimension(ClickRollup.LANGUAGE, "c.language", null, false, false),
              new Dimension(ClickRollup.ASN, "c.asn_org", "c.asn", false, true))
          .stream()
          .map(Dimension::insert)
          .toList();

  @PersistenceContext private EntityManager em;

  @Override
  public Instant rolledUntil() {
    return epoch(
        em.createNativeQuery(
                "SELECT UNIX_TIMESTAMP(rolled_until) FROM click_rollup_state WHERE id = 1")
            .getSingleResult());
  }

  @Override
  public Optional<Instant> firstClickAtOrAfter(Instant from) {
    return Optional.ofNullable(
        epoch(
            em.createNativeQuery(
                    "SELECT UNIX_TIMESTAMP(MIN(clicked_at)) FROM click_event WHERE clicked_at >= FROM_UNIXTIME(:from)")
                .setParameter("from", from.getEpochSecond())
                .getSingleResult()));
  }

  @Override
  public void rollUpDay(Instant dayStart, Instant dayEnd) {
    em.createNativeQuery(HOURLY)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("dayEnd", dayEnd.getEpochSecond())
        .executeUpdate();
    for (String sql : DIMENSIONS) {
      em.createNativeQuery(sql)
          .setParameter("dayStart", dayStart.getEpochSecond())
          .setParameter("dayEnd", dayEnd.getEpochSecond())
          .executeUpdate();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<LinkVisitor> humanVisitors(Instant from, Instant until) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT link_id, visitor_hash FROM click_event "
                    + "WHERE clicked_at >= FROM_UNIXTIME(:from) AND clicked_at < FROM_UNIXTIME(:until) AND is_bot = 0 "
                    + "AND visitor_hash IS NOT NULL ORDER BY link_id")
            .setParameter("from", from.getEpochSecond())
            .setParameter("until", until.getEpochSecond())
            .getResultList();
    return rows.stream()
        .map(r -> new LinkVisitor(((Number) r[0]).longValue(), (String) r[1]))
        .toList();
  }

  @Override
  public void saveVisitorSketch(Long linkId, Instant dayStart, byte[] sketch) {
    em.createNativeQuery(
            "INSERT INTO click_daily_visitors (link_id, day_start, sketch) "
                + "VALUES (:linkId, FROM_UNIXTIME(:dayStart), :sketch)")
        .setParameter("linkId", linkId)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("sketch", sketch)
        .executeUpdate();
  }

  @Override
  public void advanceTo(Instant rolledUntil) {
    em.createNativeQuery(
            "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(:until) WHERE id = 1")
        .setParameter("until", rolledUntil.getEpochSecond())
        .executeUpdate();
  }

  @Override
  public int deleteRawBefore(Instant before, int limit) {
    return em.createNativeQuery(
            "DELETE FROM click_event WHERE clicked_at < FROM_UNIXTIME(:before) ORDER BY clicked_at LIMIT :limit")
        .setParameter("before", before.getEpochSecond())
        .setParameter("limit", limit)
        .executeUpdate();
  }

  @Override
  public int deleteByLinkIds(Collection<Long> linkIds) {
    if (linkIds.isEmpty()) return 0;
    int deleted = 0;
    for (String table : List.of("click_hourly", "click_daily_dim", "click_daily_visitors")) {
      deleted +=
          em.createNativeQuery("DELETE FROM " + table + " WHERE link_id IN (:ids)")
              .setParameter("ids", linkIds)
              .executeUpdate();
    }
    return deleted;
  }

  private static Instant epoch(Object seconds) {
    return seconds == null ? null : Instant.ofEpochSecond(((Number) seconds).longValue());
  }
}
