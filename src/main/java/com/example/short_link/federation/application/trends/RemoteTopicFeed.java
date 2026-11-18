package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicRanking;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RemoteTopicFeed {

  static final int KEPT_IN_WINDOW = 40;

  private final TrendingTopics topics;
  private final TopicTimelines timelines;
  private final NoteTopicReader keptNotes;
  private final NoteViewReader views;
  private final RemoteAccountStandingReader standings;
  private final TrendingAuthors authors;
  private final RemoteForYou forYou;
  private final Clock clock;

  public record TopicList(List<TopicView> items) {}

  public record TopicPosts(TopicView topic, List<Object> items, String next) {}

  private record Entry(Instant at, NoteTopicReader.Kept kept, TrendingStatusReader.Read read) {

    static final Comparator<Entry> NEWEST_FIRST =
        Comparator.comparing(Entry::at)
            .reversed()
            .thenComparing(entry -> entry.kept() == null)
            .thenComparingLong(entry -> entry.kept() == null ? 0 : -entry.kept().id())
            .thenComparing(entry -> entry.read() == null ? "" : entry.read().note().uri());

    static Entry of(NoteTopicReader.Kept kept) {
      return new Entry(kept.createdAt(), kept, null);
    }

    static Entry of(TrendingStatusReader.Read read) {
      return new Entry(read.note().createdAt(), null, read);
    }
  }

  public TopicList topics(Long viewerId, String languages, int limit) {
    List<Topic> pool = topics.held().topics();
    if (pool.isEmpty()) {
      return new TopicList(List.of());
    }
    Set<String> own = forYou.ownLanguages(viewerId, languages, clock.instant());
    return new TopicList(
        TopicRanking.forReader(pool, own, limit).stream()
            .map(topic -> TopicView.of(topic, own))
            .toList());
  }

  public TopicPosts posts(Long viewerId, String languages, TopicAsk ask, String cursor, int limit) {
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    HeldTopics held = topics.held();
    Optional<Topic> topic = held.find(ask.id());
    TopicView view =
        topic
            .map(
                found ->
                    TopicView.of(found, forYou.ownLanguages(viewerId, languages, clock.instant())))
            .orElse(null);
    if (cursor != null && cursor.startsWith("r")) {
      return older(view, viewerId, ask, RemoteTrendingFeed.id(cursor), size);
    }
    List<NoteTopicReader.Kept> kept =
        keptNotes.kept(ask.carrying(), viewerId, null, KEPT_IN_WINDOW + 1);
    boolean olderKept = kept.size() > KEPT_IN_WINDOW;
    List<NoteTopicReader.Kept> inWindow = olderKept ? kept.subList(0, KEPT_IN_WINDOW) : kept;
    List<TrendingStatusReader.Read> remote =
        topic.isEmpty()
            ? List.of()
            : elsewhere(viewerId, ask, topic.get(), held, inWindow, olderKept);
    List<Entry> window = window(inWindow, remote);
    int from = Math.min(RemoteTrendingFeed.offset(cursor), window.size());
    int to = Math.min(from + size, window.size());
    String afterWindow = olderKept ? "r" + inWindow.getLast().id() : null;
    return new TopicPosts(
        view,
        items(viewerId, window.subList(from, to)),
        to < window.size() ? "t" + to : afterWindow);
  }

  private static List<Entry> window(
      List<NoteTopicReader.Kept> kept, List<TrendingStatusReader.Read> remote) {
    List<Entry> window = new ArrayList<>(kept.size() + remote.size());
    kept.forEach(row -> window.add(Entry.of(row)));
    remote.forEach(read -> window.add(Entry.of(read)));
    window.sort(Entry.NEWEST_FIRST);
    return window;
  }

  private TopicPosts older(TopicView view, Long viewerId, TopicAsk ask, Long beforeId, int size) {
    List<NoteTopicReader.Kept> rows = keptNotes.kept(ask.carrying(), viewerId, beforeId, size + 1);
    boolean more = rows.size() > size;
    List<NoteTopicReader.Kept> shown = more ? rows.subList(0, size) : rows;
    return new TopicPosts(
        view,
        items(viewerId, shown.stream().map(Entry::of).toList()),
        more ? "r" + shown.getLast().id() : null);
  }

  private List<TrendingStatusReader.Read> elsewhere(
      Long viewerId,
      TopicAsk ask,
      Topic topic,
      HeldTopics held,
      List<NoteTopicReader.Kept> inWindow,
      boolean olderKept) {
    Instant now = clock.instant();
    List<TrendingStatusReader.Read> read =
        held.posts(topic, now, () -> timelines.posts(topic, topics.elsewhere(topic)));
    Set<String> here =
        inWindow.stream()
            .map(NoteTopicReader.Kept::uri)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<TrendingStatusReader.Read> notHere =
        TrendingPosts.shown(viewerId, read, standings, now).stream()
            .filter(post -> !here.contains(post.note().uri()))
            .toList();
    if (!olderKept || notHere.isEmpty()) {
      return notHere;
    }
    Set<String> keptBelow =
        keptNotes.keptAmong(
            ask.carrying(), viewerId, notHere.stream().map(post -> post.note().uri()).toList());
    return notHere.stream().filter(post -> !keptBelow.contains(post.note().uri())).toList();
  }

  private List<Object> items(Long viewerId, List<Entry> page) {
    Map<Long, NoteViewReader.Listed> keptViews = new HashMap<>();
    List<Long> ids =
        page.stream()
            .filter(entry -> entry.kept() != null)
            .map(entry -> entry.kept().id())
            .toList();
    views.listed(viewerId, ids).forEach(row -> keptViews.put(row.id(), row));
    List<Object> items = new ArrayList<>(page.size());
    List<String> unknown = new ArrayList<>();
    for (Entry entry : page) {
      if (entry.read() != null) {
        items.add(entry.read().note().seenBy(viewerId != null));
        if (entry.read().note().author().remoteId() == null
            && !unknown.contains(entry.read().actorUri())) {
          unknown.add(entry.read().actorUri());
        }
      } else if (keptViews.containsKey(entry.kept().id())) {
        NoteViewReader.Listed row = keptViews.get(entry.kept().id());
        items.add(new RemoteTrendingFeed.KeptNote(row.view(), row.uri()));
      }
    }
    if (viewerId != null && !unknown.isEmpty()) {
      authors.warm(unknown);
    }
    return items;
  }
}
