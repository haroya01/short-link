package com.example.short_link.home.application.read;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.application.trends.RemoteForYou;
import com.example.short_link.federation.application.trends.RemoteSeen;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.foryou.ForYouProfile;
import com.example.short_link.federation.domain.foryou.HomeCandidate;
import com.example.short_link.federation.domain.foryou.HomeRanking;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PooledNote;
import com.example.short_link.post.application.read.PooledPost;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

// A cursor's pages are cut from the ranking made for its asOf, held in this process for half an
// hour; one made again after that may differ a little, as counts move.
@Service
public class HomeForYou {

  static final int LOCAL_POOL = 500;

  private final RemoteForYou remote;
  private final NoteQueryService notes;
  private final PostHomeSlices posts;
  private final NoteViewReader noteViews;
  private final LocalSeen localSeen;
  private final RemoteSeen remoteSeen;
  private final Clock clock;
  private final Cache<String, List<Ref>> rankings =
      Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(30)).build();

  record Ref(HomeCandidate.Source source, Object ref) {}

  public HomeForYou(
      RemoteForYou remote,
      NoteQueryService notes,
      PostHomeSlices posts,
      NoteViewReader noteViews,
      LocalSeen localSeen,
      RemoteSeen remoteSeen,
      Clock clock) {
    this.remote = remote;
    this.notes = notes;
    this.posts = posts;
    this.noteViews = noteViews;
    this.localSeen = localSeen;
    this.remoteSeen = remoteSeen;
    this.clock = clock;
  }

  public HomeFeedQueryService.Page page(HomeAsk ask) {
    Long viewerId = ask.viewerId();
    SeenViewer viewer = ask.viewer();
    HomeCursor.Ranked at = HomeCursor.Ranked.parse(ask.cursor());
    Instant asOf = at == null ? clock.instant().truncatedTo(ChronoUnit.MILLIS) : at.asOf();
    boolean hiding = (at == null ? ask.hideSeen() : at.hide()) && viewer != null;
    List<String> accepted = ForYouProfile.accepted(ask.languages());
    String key =
        (viewer == null ? "anon:" + String.join(",", accepted) : viewer.kind() + ":" + viewer.key())
            + (hiding ? "|hide|" : "|all|")
            + asOf.toEpochMilli();
    List<Ref> refs = rankings.get(key, made -> rank(viewerId, viewer, accepted, asOf, hiding));
    int from = Math.min(at == null ? 0 : at.offset(), refs.size());
    int to = Math.min(from + ask.size(), refs.size());
    String next = to < refs.size() ? new HomeCursor.Ranked(asOf, to, hiding).toString() : null;
    return new HomeFeedQueryService.Page(show(viewerId, refs.subList(from, to)), next);
  }

  private List<Ref> rank(
      Long viewerId, SeenViewer viewer, List<String> accepted, Instant asOf, boolean hiding) {
    HomeRanking.Weights weights = HomeRanking.Weights.DEFAULT;
    List<HomeCandidate> pool = new ArrayList<>();
    if (remote.enabled()) {
      pool.addAll(remote.homePool(viewerId, asOf));
    }
    Instant since = asOf.minus(weights.localWindow());
    for (PooledNote note : notes.pool(viewerId, since, asOf, LOCAL_POOL)) {
      pool.add(
          HomeCandidate.note(
              note.id(),
              note.userId(),
              note.language(),
              new HashSet<>(note.tags()),
              note.createdAt(),
              note.likes(),
              note.reposts(),
              note.replies()));
    }
    for (PooledPost post : posts.pool(viewerId, since, asOf, LOCAL_POOL)) {
      pool.add(
          HomeCandidate.post(
              post.id(),
              post.userId(),
              post.language(),
              new HashSet<>(post.tags()),
              post.publishedAt(),
              post.likes(),
              post.quotes(),
              post.highlights()));
    }
    HomeRanking.Reader reader =
        new HomeRanking.Reader(
            remote.reader(viewerId, viewer, accepted, asOf), viewerId, remote.follows(viewerId));
    Set<String> seen = hiding ? seen(viewer, pool, asOf) : Set.of();
    return HomeRanking.rank(pool, reader, seen, hiding, asOf, weights).items().stream()
        .map(candidate -> new Ref(candidate.source(), candidate.item().ref()))
        .toList();
  }

  private Set<String> seen(SeenViewer viewer, List<HomeCandidate> pool, Instant asOf) {
    List<SeenItem> items = new ArrayList<>();
    List<String> uris = new ArrayList<>();
    for (HomeCandidate candidate : pool) {
      switch (candidate.source()) {
        case NOTE -> items.add(new SeenItem(SeenItem.NOTE, (Long) candidate.item().ref()));
        case POST -> items.add(new SeenItem(SeenItem.POST, (Long) candidate.item().ref()));
        case REMOTE -> uris.add(candidate.key());
      }
    }
    Set<String> seen = new HashSet<>(remoteSeen.seenBefore(viewer, uris, asOf));
    for (SeenItem item : localSeen.seenBefore(viewer, items, asOf)) {
      seen.add((item.kind() == SeenItem.NOTE ? "kurl:note:" : "kurl:post:") + item.id());
    }
    return seen;
  }

  private List<HomeFeedQueryService.Item> show(Long viewerId, List<Ref> refs) {
    List<Long> noteIds = new ArrayList<>();
    List<Long> postIds = new ArrayList<>();
    List<Object> held = new ArrayList<>();
    for (Ref ref : refs) {
      if (ref.source() == HomeCandidate.Source.POST) {
        postIds.add((Long) ref.ref());
      } else if (ref.ref() instanceof Long id) {
        noteIds.add(id);
      } else {
        held.add(ref.ref());
      }
    }
    Map<Long, NoteViewReader.Listed> shownNotes = new HashMap<>();
    if (!noteIds.isEmpty()) {
      noteViews.listed(viewerId, noteIds).forEach(row -> shownNotes.put(row.id(), row));
    }
    Map<Long, PostCard> shownPosts = new HashMap<>();
    posts.cards(viewerId, postIds).forEach(card -> shownPosts.put(card.item().id(), card));
    Iterator<Object> shownHeld =
        held.isEmpty() ? List.of().iterator() : remote.showHeld(viewerId, held).iterator();
    List<HomeFeedQueryService.Item> items = new ArrayList<>(refs.size());
    for (Ref ref : refs) {
      switch (ref.source()) {
        case NOTE -> {
          NoteViewReader.Listed row = shownNotes.get((Long) ref.ref());
          if (row != null) {
            items.add(new HomeFeedQueryService.Item("note", (NoteView) row.view(), null, null));
          }
        }
        case POST -> {
          PostCard card = shownPosts.get((Long) ref.ref());
          if (card != null) {
            items.add(new HomeFeedQueryService.Item("post", null, card, null));
          }
        }
        case REMOTE -> {
          if (ref.ref() instanceof Long id) {
            NoteViewReader.Listed row = shownNotes.get(id);
            if (row != null) {
              items.add(
                  new HomeFeedQueryService.Item(
                      "remote",
                      null,
                      null,
                      new RemoteTrendingFeed.KeptNote(row.view(), row.uri())));
            }
          } else {
            items.add(new HomeFeedQueryService.Item("remote", null, null, shownHeld.next()));
          }
        }
      }
    }
    return items;
  }
}
