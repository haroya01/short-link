package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.FollowingReader;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoverFeedQueryService {

  private final CollectionConnectionRepository connectionRepository;
  private final FollowingReader following;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteBodyReader noteBodies;
  private final UserSummaryReader userSummaries;

  public DiscoverFeedView feed(Long viewerId, int page, int size, boolean forceGlobal) {
    if (forceGlobal) {
      return globalFeed(viewerId, page, size);
    }
    List<Long> followingIds = following.followingIds(viewerId);
    if (followingIds.isEmpty()) {
      return globalFeed(viewerId, page, size);
    }

    List<DiscoverConnectionRow> rows =
        connectionRepository.findPublicConnectionsByOwners(followingIds, viewerId, page, size);
    if (rows.isEmpty() && page == 0) {
      return globalFeed(viewerId, page, size);
    }
    return assemble(rows, page, size, DiscoverFeedView.SOURCE_FOLLOWING);
  }

  public DiscoverFeedView publicFeed(Long viewerId, int page, int size) {
    return globalFeed(viewerId, page, size);
  }

  private DiscoverFeedView globalFeed(Long viewerId, int page, int size) {
    List<DiscoverConnectionRow> rows =
        connectionRepository.findRecentPublicConnections(viewerId, page, size);
    return assemble(rows, page, size, DiscoverFeedView.SOURCE_GLOBAL);
  }

  private DiscoverFeedView assemble(
      List<DiscoverConnectionRow> rows, int page, int size, String source) {
    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(refIds(rows, "HIGHLIGHT")).stream()
                .filter(PostAccessPolicy::saidOpenly)
                .toList(),
            PostHighlightEntity::getId);
    Map<Long, NoteBlock> notes = noteBodies.blocksByIds(refIds(rows, "NOTE"));

    Set<Long> postIds = new HashSet<>(refIds(rows, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostAccessPolicy::isOpen)
            .filter(PostEntity::isDiscoverable)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    Set<Long> userIds =
        rows.stream().map(DiscoverConnectionRow::ownerId).collect(Collectors.toSet());
    posts.values().forEach(p -> userIds.add(p.getUserId()));
    Map<Long, UserSummary> users = bulk(userSummaries.findAll(userIds), UserSummary::id);

    List<DiscoverConnectionView> items = new ArrayList<>();
    for (DiscoverConnectionRow row : rows) {
      UserSummary curator = users.get(row.ownerId());
      if (curator == null) continue;
      if (!DiscoveryQuality.isMeaningfulLabel(row.collectionTitle())) continue;
      DiscoverConnectionView view = resolve(row, curator, posts, highlights, notes, users);
      if (view != null) items.add(view);
    }

    return new DiscoverFeedView(items, page, size, rows.size() == size, source);
  }

  private DiscoverConnectionView resolve(
      DiscoverConnectionRow row,
      UserSummary curator,
      Map<Long, PostEntity> posts,
      Map<Long, PostHighlightEntity> highlights,
      Map<Long, NoteBlock> notes,
      Map<Long, UserSummary> users) {
    PublicAuthorView curatorView = PublicAuthorView.from(curator);
    return switch (row.blockType()) {
      case POST -> {
        PostEntity post = posts.get(row.refId());
        if (post == null) yield null;
        UserSummary author = users.get(post.getUserId());
        yield view(
            row,
            curatorView,
            "POST",
            post.getTitle(),
            post.getExcerpt(),
            post.getSlug(),
            author == null ? null : author.username(),
            null,
            null,
            null);
      }
      case HIGHLIGHT -> {
        PostHighlightEntity hl = highlights.get(row.refId());
        if (hl == null) yield null;
        PostEntity post = posts.get(hl.getPostId());
        if (post == null) yield null;
        UserSummary author = users.get(post.getUserId());
        yield view(
            row,
            curatorView,
            "HIGHLIGHT",
            post.getTitle(),
            null,
            post.getSlug(),
            author == null ? null : author.username(),
            hl.getQuote(),
            null,
            null);
      }
      case NOTE -> {
        NoteBlock note = notes.get(row.refId());
        if (note == null || !DiscoveryQuality.isMeaningfulLabel(note.body())) yield null;
        yield view(
            row,
            curatorView,
            "NOTE",
            null,
            null,
            null,
            note.authorUsername(),
            null,
            note.body(),
            note.id());
      }
    };
  }

  private static DiscoverConnectionView view(
      DiscoverConnectionRow row,
      PublicAuthorView curator,
      String blockType,
      String title,
      String excerpt,
      String slug,
      String username,
      String quote,
      String body,
      Long noteId) {
    return new DiscoverConnectionView(
        row.connectionId(),
        curator,
        row.collectionId(),
        row.collectionTitle(),
        CollectionKind.of(row.ordered()).name(),
        row.ordered(),
        DiscoveryQuality.isMeaningfulLabel(row.why()) ? row.why() : null,
        row.connectedAt(),
        blockType,
        title,
        excerpt,
        slug,
        username,
        quote,
        body,
        noteId);
  }

  private static List<Long> refIds(List<DiscoverConnectionRow> rows, String type) {
    return rows.stream()
        .filter(r -> r.blockType().name().equals(type))
        .map(DiscoverConnectionRow::refId)
        .distinct()
        .toList();
  }

  private static <T> Map<Long, T> bulk(List<T> items, Function<T, Long> key) {
    return items.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
