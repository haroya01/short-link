package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouProfile;
import com.example.short_link.federation.domain.foryou.ForYouRanking;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

// A cursor's pages are cut from the ranking made for its asOf, held in this process for half an
// hour; one made again after that may differ a little, as counts move.
@Service
public class RemoteForYou {

  static final Duration WINDOW = Duration.ofHours(72);
  static final Duration SIGNAL_WINDOW = Duration.ofDays(30);
  static final int POOL = 1_000;
  static final int PER_KIND = 40;
  static final int MIN_DWELL_MS = 8_000;

  private final LatestProperties latestProps;
  private final LatestPosts latest;
  private final TrendingPosts trending;
  private final TrendingAuthors authors;
  private final NoteViewReader notes;
  private final RemoteNoteReachRepository reach;
  private final ForYouSignalReader signals;
  private final RemoteSeen seen;
  private final Clock clock;
  private final Cache<String, Ranking> rankings =
      Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(30)).build();

  private record Ranking(List<Object> refs, int seenFrom) {}

  private record Position(Instant asOf, int offset) {}

  public RemoteForYou(
      LatestProperties latestProps,
      LatestPosts latest,
      TrendingPosts trending,
      TrendingAuthors authors,
      NoteViewReader notes,
      RemoteNoteReachRepository reach,
      ForYouSignalReader signals,
      RemoteSeen seen,
      Clock clock) {
    this.latestProps = latestProps;
    this.latest = latest;
    this.trending = trending;
    this.authors = authors;
    this.notes = notes;
    this.reach = reach;
    this.signals = signals;
    this.seen = seen;
    this.clock = clock;
  }

  public RemoteTrendingFeed.Page page(
      Long viewerId,
      SeenViewer viewer,
      String languages,
      String cursor,
      int limit,
      boolean hideSeen) {
    if (!latestProps.enabled()) {
      return new RemoteTrendingFeed.Page(List.of(), null);
    }
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    Position at = position(cursor);
    boolean stored = latestProps.pulling();
    List<String> accepted = ForYouProfile.accepted(languages);
    String key =
        (stored ? "s|" : "m|")
            + (viewer == null
                ? "anon:" + String.join(",", accepted)
                : viewer.kind() + ":" + viewer.key())
            + "|"
            + at.asOf().toEpochMilli();
    Ranking ranking = rankings.getIfPresent(key);
    if (ranking == null) {
      ranking = rank(viewerId, viewer, accepted, at.asOf(), stored);
      rankings.put(key, ranking);
    }
    int end = hideSeen && ranking.seenFrom() >= 0 ? ranking.seenFrom() : ranking.refs().size();
    int from = Math.min(at.offset(), end);
    int to = Math.min(from + size, end);
    List<Object> slice = ranking.refs().subList(from, to);
    List<Object> items = new ArrayList<>(slice.size());
    Integer seenFrom = null;
    if (stored && !slice.isEmpty()) {
      Map<Long, NoteViewReader.Listed> byId = new HashMap<>();
      notes
          .listed(viewerId, slice.stream().map(Long.class::cast).toList())
          .forEach(row -> byId.put(row.id(), row));
      for (int i = from; i < to; i++) {
        NoteViewReader.Listed row = byId.get((Long) ranking.refs().get(i));
        if (row != null) {
          seenFrom = seenFrom == null ? seenAt(ranking, from, i, items.size()) : seenFrom;
          items.add(new RemoteTrendingFeed.KeptNote(row.view(), row.uri()));
        }
      }
    } else if (!stored) {
      List<String> unknown = new ArrayList<>();
      for (int i = from; i < to; i++) {
        TrendingStatusReader.Read read = (TrendingStatusReader.Read) ranking.refs().get(i);
        seenFrom = seenFrom == null ? seenAt(ranking, from, i, items.size()) : seenFrom;
        items.add(read.note().seenBy(viewerId != null));
        if (read.note().author().remoteId() == null && !unknown.contains(read.actorUri())) {
          unknown.add(read.actorUri());
        }
      }
      if (viewerId != null && !unknown.isEmpty()) {
        authors.warm(unknown);
      }
    }
    String next = to < end ? "f" + at.asOf().toEpochMilli() + "." + to : null;
    return new RemoteTrendingFeed.Page(items, next, null, seenFrom);
  }

  private static Integer seenAt(Ranking ranking, int from, int index, int shown) {
    return ranking.seenFrom() >= from && index >= ranking.seenFrom() ? shown : null;
  }

  private Ranking rank(
      Long viewerId, SeenViewer viewer, List<String> accepted, Instant asOf, boolean stored) {
    List<ForYouProfile.Signal> read = new ArrayList<>();
    if (viewer != null) {
      for (ForYouSignalReader.Signal signal :
          signals.signals(viewer, asOf.minus(SIGNAL_WINDOW), PER_KIND, MIN_DWELL_MS)) {
        read.add(
            new ForYouProfile.Signal(
                weight(signal.kind()), signal.author(), signal.language(), signal.tags()));
      }
    }
    List<String> languages =
        viewerId == null ? accepted : signals.locale(viewerId).map(List::of).orElse(List.of());
    ForYouReader reader = ForYouProfile.of(languages, read);
    List<ForYouCandidate> pool = stored ? stored(viewerId, asOf) : held(viewerId, asOf);
    Set<String> seenUris =
        viewer == null || pool.isEmpty()
            ? Set.of()
            : seen.seenBefore(viewer, pool.stream().map(ForYouCandidate::uri).toList(), asOf);
    ForYouRanking.Ranked ranked = ForYouRanking.rank(pool, reader, seenUris, asOf);
    return new Ranking(
        ranked.items().stream().map(ForYouCandidate::ref).toList(), ranked.seenFrom());
  }

  private List<ForYouCandidate> stored(Long viewerId, Instant asOf) {
    List<NoteViewReader.Pooled> rows = notes.remotePool(viewerId, asOf.minus(WINDOW), asOf, POOL);
    Map<Long, RemoteNoteReachRepository.Reach> counts =
        reach.of(rows.stream().map(NoteViewReader.Pooled::id).toList());
    List<ForYouCandidate> pool = new ArrayList<>(rows.size());
    for (NoteViewReader.Pooled row : rows) {
      RemoteNoteReachRepository.Reach known = counts.get(row.id());
      if (known != null && known.gone()) {
        continue;
      }
      RemoteNoteReachRepository.Counts count =
          known == null ? RemoteNoteReachRepository.Counts.NONE : known.counts();
      pool.add(
          new ForYouCandidate(
              row.id(),
              row.uri() == null ? "note:" + row.id() : row.uri(),
              "r" + row.remoteActorId(),
              row.domain(),
              row.language(),
              new HashSet<>(row.tags()),
              row.createdAt(),
              count.likes(),
              count.reposts(),
              count.replies(),
              row.id()));
    }
    return pool;
  }

  private List<ForYouCandidate> held(Long viewerId, Instant asOf) {
    Map<String, TrendingStatusReader.Read> byUri = new LinkedHashMap<>();
    trending.pool(viewerId).forEach(read -> byUri.putIfAbsent(read.note().uri(), read));
    latest.pool(viewerId).forEach(read -> byUri.putIfAbsent(read.note().uri(), read));
    Set<Long> followed = viewerId == null ? Set.of() : signals.followed(viewerId);
    Instant since = asOf.minus(WINDOW);
    List<ForYouCandidate> pool = new ArrayList<>(byUri.size());
    for (TrendingStatusReader.Read read : byUri.values()) {
      TrendingNote note = read.note();
      Long remoteId = note.author().remoteId();
      if (note.createdAt().isBefore(since)
          || note.createdAt().isAfter(asOf)
          || note.sensitive()
          || (note.contentWarning() != null && !note.contentWarning().isBlank())
          || (remoteId != null && followed.contains(remoteId))) {
        continue;
      }
      pool.add(
          new ForYouCandidate(
              read,
              note.uri(),
              remoteId == null ? read.actorUri() : "r" + remoteId,
              read.domain(),
              note.language(),
              new HashSet<>(Hashtags.of(note.body())),
              note.createdAt(),
              note.likeCount(),
              note.repostCount(),
              note.replyCount(),
              note.createdAt().toEpochMilli()));
    }
    return pool;
  }

  private static double weight(ForYouSignalReader.Kind kind) {
    return switch (kind) {
      case LIKED -> ForYouProfile.LIKED;
      case BOOKMARKED -> ForYouProfile.BOOKMARKED;
      case ANSWERED -> ForYouProfile.ANSWERED;
      case FOLLOWED -> ForYouProfile.FOLLOWED;
      case DWELT -> ForYouProfile.DWELT;
    };
  }

  private Position position(String cursor) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    int dot = cursor == null || !cursor.startsWith("f") ? -1 : cursor.indexOf('.');
    if (dot < 2) {
      return new Position(now, 0);
    }
    try {
      long asOf = Long.parseLong(cursor.substring(1, dot));
      int offset = Integer.parseInt(cursor.substring(dot + 1));
      return asOf >= 0 && offset >= 0
          ? new Position(Instant.ofEpochMilli(asOf), offset)
          : new Position(now, 0);
    } catch (NumberFormatException malformed) {
      return new Position(now, 0);
    }
  }
}
