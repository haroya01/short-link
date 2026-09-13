package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
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

/**
 * 팔로우한 큐레이터의 PUBLIC 컬렉션 연결을 최신순으로 반환한다. 팔로우가 없거나 첫 페이지가 비면 전역 피드로 폴백하며, 이후 빈 페이지는 종료다. 대상이 사라진 연결은
 * 제외하고 응답의 {@code source}로 피드 종류를 구분한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoverFeedQueryService {

  private final CollectionConnectionRepository connectionRepository;
  private final FollowRepository followRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteRepository noteRepository;
  private final UserRepository userRepository;

  public DiscoverFeedView feed(Long viewerId, int page, int size, boolean forceGlobal) {
    if (forceGlobal) {
      return globalFeed(page, size);
    }
    List<Long> followingIds = followRepository.findFollowingIds(viewerId);
    if (followingIds.isEmpty()) {
      return globalFeed(page, size);
    }

    List<DiscoverConnectionRow> rows =
        connectionRepository.findPublicConnectionsByOwners(followingIds, page, size);
    if (rows.isEmpty() && page == 0) {
      return globalFeed(page, size);
    }
    return assemble(rows, page, size, DiscoverFeedView.SOURCE_FOLLOWING);
  }

  public DiscoverFeedView publicFeed(int page, int size) {
    return globalFeed(page, size);
  }

  private DiscoverFeedView globalFeed(int page, int size) {
    List<DiscoverConnectionRow> rows = connectionRepository.findRecentPublicConnections(page, size);
    return assemble(rows, page, size, DiscoverFeedView.SOURCE_GLOBAL);
  }

  private DiscoverFeedView assemble(
      List<DiscoverConnectionRow> rows, int page, int size, String source) {
    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(refIds(rows, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, NoteEntity> notes =
        bulk(noteRepository.findAllByIdIn(refIds(rows, "NOTE")), NoteEntity::getId);

    Set<Long> postIds = new HashSet<>(refIds(rows, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    // 원문이 미발행이면 글과 그 하이라이트 인용을 모두 제외한다. 소유자에게도 같은 규칙을 적용한다.
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    Set<Long> userIds =
        rows.stream().map(DiscoverConnectionRow::ownerId).collect(Collectors.toSet());
    posts.values().forEach(p -> userIds.add(p.getUserId()));
    Map<Long, UserEntity> users = bulk(userRepository.findAllByIdIn(userIds), UserEntity::getId);

    List<DiscoverConnectionView> items = new ArrayList<>();
    for (DiscoverConnectionRow row : rows) {
      UserEntity curator = users.get(row.ownerId());
      if (curator == null) continue;
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
      Map<Long, NoteEntity> notes,
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
            null);
      }
      case NOTE -> {
        NoteEntity note = notes.get(row.refId());
        if (note == null) yield null;
        yield view(row, curatorView, "NOTE", null, null, null, null, null, note.getBody());
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
      String body) {
    return new DiscoverConnectionView(
        row.connectionId(),
        curator,
        row.collectionId(),
        row.collectionTitle(),
        row.kind() == null ? "COLLECTION" : row.kind().name(),
        row.why(),
        row.connectedAt(),
        blockType,
        title,
        excerpt,
        slug,
        username,
        quote,
        body);
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
