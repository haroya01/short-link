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
        COALESCE(%s, -1) AS age_day,
        SUM(c.is_bot = 0), SUM(c.is_bot = 1),
        FROM_UNIXTIME(MIN(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END)),
        FROM_UNIXTIME(MAX(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END)),
        MAX(c.clicked_at)
      FROM click_event c LEFT JOIN link l ON l.id = c.link_id
      WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)
      GROUP BY c.link_id, hour_start, age_day
      """
          .formatted(ClickRollup.AGE_DAY);

  private static final String RECENT_QUARTERS =
      """
      INSERT INTO click_recent_quarter
        (link_id, quarter_start, age_day, humans, bots, first_human_at, last_human_at)
      SELECT c.link_id,
        FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(c.clicked_at) / 900) * 900) AS quarter_start,
        COALESCE(%s, -1) AS age_day,
        SUM(c.is_bot = 0), SUM(c.is_bot = 1),
        FROM_UNIXTIME(MIN(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END)),
        FROM_UNIXTIME(MAX(CASE WHEN c.is_bot = 0 THEN UNIX_TIMESTAMP(c.clicked_at) END))
      FROM click_event c LEFT JOIN link l ON l.id = c.link_id
      WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)
      GROUP BY c.link_id, quarter_start, age_day
      """
          .formatted(ClickRollup.AGE_DAY);

  private record Dimension(
      int code, String value, String num, boolean keepNull, boolean bots, String regionCode) {

    Dimension(int code, String value, String num, boolean keepNull, boolean bots) {
      this(code, value, num, keepNull, bots, "NULL");
    }

    String insert(String table) {
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
      return "INSERT INTO "
          + table
          + " (link_id, dim, day_start, is_bot, value_key, value, num, region_code, clicks, first_at) "
          + "SELECT c.link_id, "
          + code
          + ", FROM_UNIXTIME(:dayStart), c.is_bot, "
          + key
          + ", "
          + (value == null ? "NULL" : "LEFT(" + value + ", 2048)")
          + ", "
          + n
          + ", "
          + regionCode
          + ", COUNT(*), MIN(c.clicked_at) FROM click_event c "
          + "WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)"
          + (bots ? "" : " AND c.is_bot = 0")
          + (keepNull ? "" : " AND " + present + " IS NOT NULL")
          + " GROUP BY c.link_id, c.is_bot, "
          + groupBy;
    }
  }

  private static final List<Dimension> DIMENSIONS =
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
          new Dimension(
              ClickRollup.REGION, "c.region_name", null, false, false, "MAX(c.region_code)"),
          new Dimension(ClickRollup.CITY, "c.city_name", null, false, false),
          new Dimension(ClickRollup.LANGUAGE, "c.language", null, false, false),
          new Dimension(ClickRollup.ASN, "c.asn_org", "c.asn", false, true));

  private static final List<String> DAILY_DIMENSIONS =
      DIMENSIONS.stream().map(d -> d.insert("click_daily_dim")).toList();

  private static final List<String> RECENT_DIMENSIONS =
      DIMENSIONS.stream().map(d -> d.insert("click_recent_dim")).toList();

  private static final String ONE_DAY =
      "WHERE c.clicked_at >= FROM_UNIXTIME(:dayStart) AND c.clicked_at < FROM_UNIXTIME(:dayEnd)";

  private static final String LINK_RECENT_QUARTERS = ofOneLink(RECENT_QUARTERS);

  private static final List<String> LINK_RECENT_DIMENSIONS =
      RECENT_DIMENSIONS.stream().map(ClickRollupRepositoryAdapter::ofOneLink).toList();

  private static String ofOneLink(String sql) {
    return sql.replace(ONE_DAY, ONE_DAY + " AND c.link_id = :linkId");
  }

  @PersistenceContext private EntityManager em;

  @Override
  public Instant rolledUntil() {
    return epoch(
        em.createNativeQuery(
                "SELECT UNIX_TIMESTAMP(rolled_until) FROM click_rollup_state WHERE id = 1")
            .getSingleResult());
  }

  @Override
  public State lockState() {
    Object[] row =
        (Object[])
            em.createNativeQuery(
                    "SELECT UNIX_TIMESTAMP(rolled_until), UNIX_TIMESTAMP("
                        + "GREATEST(rolled_until, COALESCE(aggregated_until, rolled_until))) "
                        + "FROM click_rollup_state WHERE id = 1 FOR UPDATE")
                .getSingleResult();
    return new State(epoch(row[0]), epoch(row[1]));
  }

  @Override
  public Instant aggregatedUntil() {
    return epoch(
        em.createNativeQuery(
                "SELECT UNIX_TIMESTAMP(aggregated_until) FROM click_rollup_state WHERE id = 1")
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
    for (String sql : DAILY_DIMENSIONS) {
      em.createNativeQuery(sql)
          .setParameter("dayStart", dayStart.getEpochSecond())
          .setParameter("dayEnd", dayEnd.getEpochSecond())
          .executeUpdate();
    }
  }

  @Override
  public void aggregateDay(Instant dayStart, Instant dayEnd) {
    deleteRecent(dayStart, dayEnd);
    em.createNativeQuery(RECENT_QUARTERS)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("dayEnd", dayEnd.getEpochSecond())
        .executeUpdate();
    for (String sql : RECENT_DIMENSIONS) {
      em.createNativeQuery(sql)
          .setParameter("dayStart", dayStart.getEpochSecond())
          .setParameter("dayEnd", dayEnd.getEpochSecond())
          .executeUpdate();
    }
  }

  @Override
  public void aggregateLinkDay(Long linkId, Instant dayStart, Instant dayEnd) {
    em.createNativeQuery(
            "DELETE FROM click_recent_quarter WHERE link_id = :linkId "
                + "AND quarter_start >= FROM_UNIXTIME(:dayStart) "
                + "AND quarter_start < FROM_UNIXTIME(:dayEnd)")
        .setParameter("linkId", linkId)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("dayEnd", dayEnd.getEpochSecond())
        .executeUpdate();
    em.createNativeQuery(
            "DELETE FROM click_recent_dim WHERE link_id = :linkId "
                + "AND day_start >= FROM_UNIXTIME(:dayStart) AND day_start < FROM_UNIXTIME(:dayEnd)")
        .setParameter("linkId", linkId)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("dayEnd", dayEnd.getEpochSecond())
        .executeUpdate();
    em.createNativeQuery(LINK_RECENT_QUARTERS)
        .setParameter("linkId", linkId)
        .setParameter("dayStart", dayStart.getEpochSecond())
        .setParameter("dayEnd", dayEnd.getEpochSecond())
        .executeUpdate();
    for (String sql : LINK_RECENT_DIMENSIONS) {
      em.createNativeQuery(sql)
          .setParameter("linkId", linkId)
          .setParameter("dayStart", dayStart.getEpochSecond())
          .setParameter("dayEnd", dayEnd.getEpochSecond())
          .executeUpdate();
    }
  }

  @Override
  public void advanceAggregatedTo(Instant aggregatedUntil) {
    em.createNativeQuery(
            "UPDATE click_rollup_state SET aggregated_until = "
                + "GREATEST(aggregated_until, FROM_UNIXTIME(:until)) WHERE id = 1")
        .setParameter("until", aggregatedUntil.getEpochSecond())
        .executeUpdate();
  }

  private void deleteRecent(Instant from, Instant until) {
    em.createNativeQuery(
            "DELETE FROM click_recent_quarter WHERE quarter_start >= FROM_UNIXTIME(:from) "
                + "AND quarter_start < FROM_UNIXTIME(:until)")
        .setParameter("from", from.getEpochSecond())
        .setParameter("until", until.getEpochSecond())
        .executeUpdate();
    em.createNativeQuery(
            "DELETE FROM click_recent_dim WHERE day_start >= FROM_UNIXTIME(:from) "
                + "AND day_start < FROM_UNIXTIME(:until)")
        .setParameter("from", from.getEpochSecond())
        .setParameter("until", until.getEpochSecond())
        .executeUpdate();
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
    deleteRecent(Instant.EPOCH, rolledUntil);
    em.createNativeQuery(
            "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(:until), "
                + "aggregated_until = GREATEST(aggregated_until, FROM_UNIXTIME(:until)) WHERE id = 1")
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
    for (String table :
        List.of(
            "click_hourly",
            "click_daily_dim",
            "click_daily_visitors",
            "click_recent_quarter",
            "click_recent_dim")) {
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
