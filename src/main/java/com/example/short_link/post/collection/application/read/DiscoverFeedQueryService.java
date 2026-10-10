package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
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
  private final FollowRepository followRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteBodyReader noteBodies;
  private final UserRepository userRepository;

  public DiscoverFeedView feed(Long viewerId, int page, int size, boolean forceGlobal) {
    if (forceGlobal) {
      return globalFeed(viewerId, page, size);
    }
    List<Long> followingIds = followRepository.findFollowingIds(viewerId);
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
            highlightRepository.findAllByIdIn(refIds(rows, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, NoteBlock> notes = noteBodies.blocksByIds(refIds(rows, "NOTE"));

    Set<Long> postIds = new HashSet<>(refIds(rows, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    // 원문이 미발행이거나 발견 품질 하한선 아래면 글과 그 하이라이트 인용을 모두 제외한다. 소유자에게도 같은 규칙이다.
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .filter(PostEntity::isDiscoverable)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    Set<Long> userIds =
        rows.stream().map(DiscoverConnectionRow::ownerId).collect(Collectors.toSet());
    posts.values().forEach(p -> userIds.add(p.getUserId()));
    Map<Long, UserEntity> users = bulk(userRepository.findAllByIdIn(userIds), UserEntity::getId);

    List<DiscoverConnectionView> items = new ArrayList<>();
    for (DiscoverConnectionRow row : rows) {
      UserEntity curator = users.get(row.ownerId());
      if (curator == null) continue;
      if (!DiscoveryQuality.isMeaningfulLabel(row.collectionTitle())) continue;
      DiscoverConnectionView view = resolve(row, curator, posts, highlights, notes, users);
      if (view != null) items.add(view);
    }

    return new DiscoverFeedView(items, page, size, rows.size() == size, source);
  }

  private DiscoverConnectionView resolve(
      DiscoverConnectionRow row,
      UserEntity curator,
      Map<Long, PostEntity> posts,
      Map<Long, PostHighlightEntity> highlights,
      Map<Long, NoteBlock> notes,
      Map<Long, UserEntity> users) {
    PublicAuthorView curatorView = PublicAuthorView.from(curator);
    return switch (row.blockType()) {
      case POST -> {
        PostEntity post = posts.get(row.refId());
        if (post == null) yield null;
        UserEntity author = users.get(post.getUserId());
        yield view(
            row,
            curatorView,
            "POST",
            post.getTitle(),
            post.getExcerpt(),
            post.getSlug(),
            author == null ? null : author.getUsername(),
            null,
            null,
            null);
      }
      case HIGHLIGHT -> {
        PostHighlightEntity hl = highlights.get(row.refId());
        if (hl == null) yield null;
        PostEntity post = posts.get(hl.getPostId());
        if (post == null) yield null;
        UserEntity author = users.get(post.getUserId());
        yield view(
            row,
            curatorView,
            "HIGHLIGHT",
            post.getTitle(),
            null,
            post.getSlug(),
            author == null ? null : author.getUsername(),
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
        row.kind() == null ? "COLLECTION" : row.kind().name(),
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
