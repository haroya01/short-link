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
 * 발견 = 큐레이터의 연결을 따라가기(§0). 내가 팔로우한 사람들의 *공개* 컬렉션에 최근 이어진 연결을 최신순으로 흘린다 — 알고리즘 랭킹이 아니라 사람의 큐레이션.
 * 블록·큐레이터를 일괄 해석(N+1 없이) 하고, 대상이 사라진 연결은 건너뛴다. 팔로우가 없으면 빈 피드(콜드스타트는 클라가 안내).
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

  public DiscoverFeedView feed(Long viewerId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(viewerId);
    if (followingIds.isEmpty()) {
      return new DiscoverFeedView(List.of(), page, size, false);
    }

    List<DiscoverConnectionRow> rows =
        connectionRepository.findPublicConnectionsByOwners(followingIds, page, size);

    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(refIds(rows, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, NoteEntity> notes =
        bulk(noteRepository.findAllByIdIn(refIds(rows, "NOTE")), NoteEntity::getId);

    Set<Long> postIds = new HashSet<>(refIds(rows, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    Map<Long, PostEntity> posts = bulk(postRepository.findAllByIdIn(postIds), PostEntity::getId);

    // 큐레이터(컬렉션 주인) + 블록 작가 — 한 번에.
    Set<Long> userIds =
        rows.stream().map(DiscoverConnectionRow::ownerId).collect(Collectors.toSet());
    posts.values().forEach(p -> userIds.add(p.getUserId()));
    Map<Long, UserEntity> users = bulk(userRepository.findAllByIdIn(userIds), UserEntity::getId);

    List<DiscoverConnectionView> items = new ArrayList<>();
    for (DiscoverConnectionRow row : rows) {
      UserEntity curator = users.get(row.ownerId());
      if (curator == null) continue; // 큐레이터 소실 — 흐름에서 뺀다.
      DiscoverConnectionView view = resolve(row, curator, posts, highlights, notes, users);
      if (view != null) items.add(view);
    }

    return new DiscoverFeedView(items, page, size, rows.size() == size);
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
        UserEntity author = post == null ? null : users.get(post.getUserId());
        yield view(
            row,
            curatorView,
            "HIGHLIGHT",
            post == null ? null : post.getTitle(),
            null,
            post == null ? null : post.getSlug(),
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
