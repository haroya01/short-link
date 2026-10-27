package com.example.short_link.note.infrastructure.persistence;

import static com.example.short_link.note.infrastructure.persistence.NoteSql.TRENDING_WINDOW;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.heardNote;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.inLanguages;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.notReply;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.trendingSql;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.unlimited;
import static com.example.short_link.note.infrastructure.persistence.NoteSql.viewer;

import com.example.short_link.common.discovery.TrendingSpread;
import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.TrendingLink;
import com.example.short_link.note.domain.TrendingTag;
import com.example.short_link.note.domain.repository.NoteTrendsReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteTrendsReaderAdapter implements NoteTrendsReader {

  private final JpaNoteRepository jpa;
  private final TrendingSpread spread;
  private final TrendingNoteRanking ranking;

  @PersistenceContext private EntityManager em;

  @Override
  public List<NoteEntity> trending(Long viewerId, int offset, int limit) {
    List<Long> ranked = ranking.ids();
    List<Long> shown = shownOf(ranked, viewerId);
    if (offset + limit > shown.size() && ranked.size() >= TrendingNoteRanking.SIZE) {
      return trendingLive(viewerId, offset, limit);
    }
    List<Long> page =
        shown.subList(Math.min(offset, shown.size()), Math.min(offset + limit, shown.size()));
    Map<Long, NoteEntity> found = new HashMap<>();
    findAllByIdIn(page).forEach(note -> found.put(note.getId(), note));
    return page.stream().map(found::get).filter(Objects::nonNull).toList();
  }

  @SuppressWarnings("unchecked")
  private List<Long> shownOf(List<Long> ranked, Long viewerId) {
    if (ranked.isEmpty()) {
      return List.of();
    }
    Set<Long> shown =
        new HashSet<>(
            ((List<Number>)
                    em.createNativeQuery(
                            "SELECT n.id FROM note n WHERE n.id IN (:ids) AND "
                                + notReply("n")
                                + " AND "
                                + NoteAccessSql.listed("n")
                                + " AND n.created_at >= :since"
                                + heardNote("n")
                                + unlimited("n")
                                + inLanguages("n"))
                        .setParameter("ids", ranked)
                        .setParameter("since", Instant.now().minus(TRENDING_WINDOW))
                        .setParameter("viewer", viewer(viewerId))
                        .setParameter("now", Instant.now())
                        .getResultList())
                .stream().map(Number::longValue).toList());
    return ranked.stream().filter(shown::contains).toList();
  }

  @SuppressWarnings("unchecked")
  private List<NoteEntity> trendingLive(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            trendingSql("o.*", unlimited("n") + inLanguages("n")), NoteEntity.class)
        .setParameter("perAuthor", spread.perAuthor())
        .setParameter("since", Instant.now().minus(TRENDING_WINDOW))
        .setParameter("viewer", viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public List<TrendingTag> trendingTags(Instant now, int days, int minAccounts, int limit) {
    var query =
        em.createNativeQuery(
            "SELECT g.tag, COUNT(DISTINCT COALESCE(n.user_id, -n.remote_actor_id)) AS accounts,"
                + " COUNT(*) AS uses"
                + perDay(days)
                + " FROM note_tag g JOIN note n ON n.id = g.note_id"
                + " WHERE n.created_at >= :d0 AND "
                + NoteAccessSql.listed("n")
                + trendable("n")
                + " GROUP BY g.tag HAVING accounts >= :minAccounts"
                + " ORDER BY accounts DESC, uses DESC, g.tag LIMIT :limit");
    bindDays(query, now, days);
    List<?> rows =
        query.setParameter("minAccounts", minAccounts).setParameter("limit", limit).getResultList();
    List<TrendingTag> tags = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] columns = (Object[]) raw;
      tags.add(
          new TrendingTag(
              (String) columns[0],
              ((Number) columns[1]).longValue(),
              ((Number) columns[2]).longValue(),
              history(columns, 3, days)));
    }
    return tags;
  }

  @Override
  public List<TrendingLink> trendingLinks(Instant now, int days, int minAccounts, int limit) {
    var query =
        em.createNativeQuery(
            "SELECT p.url, MAX(p.title), MAX(p.description), MAX(p.image_url),"
                + " COUNT(DISTINCT COALESCE(n.user_id, -n.remote_actor_id)) AS accounts,"
                + " COUNT(*) AS uses"
                + perDay(days)
                + " FROM note_link_preview p JOIN note n ON n.id = p.note_id"
                + " WHERE n.created_at >= :d0 AND "
                + NoteAccessSql.listed("n")
                + trendable("n")
                + " GROUP BY p.url HAVING accounts >= :minAccounts"
                + " ORDER BY accounts DESC, uses DESC, p.url LIMIT :limit");
    bindDays(query, now, days);
    List<?> rows =
        query.setParameter("minAccounts", minAccounts).setParameter("limit", limit).getResultList();
    List<TrendingLink> links = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] columns = (Object[]) raw;
      links.add(
          new TrendingLink(
              (String) columns[0],
              (String) columns[1],
              (String) columns[2],
              (String) columns[3],
              ((Number) columns[4]).longValue(),
              ((Number) columns[5]).longValue(),
              history(columns, 6, days)));
    }
    return links;
  }

  // A note from elsewhere trends only once a member liked, reposted or answered it: that touch
  // stands in for moderator review. Never from a server moderators blocked.
  private static String trendable(String alias) {
    return " AND ("
        + alias
        + ".remote_actor_id IS NULL"
        + " OR EXISTS (SELECT 1 FROM note_like ml WHERE ml.note_id = "
        + alias
        + ".id)"
        + " OR EXISTS (SELECT 1 FROM note_repost mr WHERE mr.note_id = "
        + alias
        + ".id)"
        + " OR EXISTS (SELECT 1 FROM note mc WHERE mc.in_reply_to_id = "
        + alias
        + ".id"
        + " AND mc.user_id IS NOT NULL))"
        + " AND NOT EXISTS (SELECT 1 FROM federation_remote_actor ta WHERE ta.id = "
        + alias
        + ".remote_actor_id AND "
        + ServerBlockSql.blocks("ta.domain")
        + ")";
  }

  // The last day has no end: a note stamped a little ahead (another server's clock) is today's.
  private static String perDay(int days) {
    StringBuilder perDay = new StringBuilder();
    for (int day = 0; day < days; day++) {
      perDay.append(", SUM(n.created_at >= :d").append(day);
      if (day < days - 1) {
        perDay.append(" AND n.created_at < :d").append(day + 1);
      }
      perDay.append(")");
    }
    return perDay.toString();
  }

  private static void bindDays(jakarta.persistence.Query query, Instant now, int days) {
    for (int day = 0; day < days; day++) {
      query.setParameter("d" + day, now.minus(Duration.ofDays(days - day)));
    }
  }

  private static List<Long> history(Object[] columns, int from, int days) {
    List<Long> history = new ArrayList<>(days);
    for (int day = 0; day < days; day++) {
      Object count = columns[from + day];
      history.add(count == null ? 0L : ((Number) count).longValue());
    }
    return List.copyOf(history);
  }

  private List<NoteEntity> findAllByIdIn(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllByIdIn(ids);
  }
}
