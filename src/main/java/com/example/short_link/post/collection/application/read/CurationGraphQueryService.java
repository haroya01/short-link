package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CooccurrenceRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CuratorOverlapRow;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurationGraphQueryService {

  private static final int MAX_LIMIT = 24;

  private final CollectionConnectionRepository connectionRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteBodyReader noteBodies;
  private final UserRepository userRepository;

  public List<RelatedBlockView> relatedTo(ConnectionBlockType blockType, Long refId, int limit) {
    List<CooccurrenceRow> rows =
        connectionRepository.findCooccurring(blockType, refId, clamp(limit));
    if (rows.isEmpty()) return List.of();

    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(refIds(rows, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, String> notes = noteBodies.bodiesByIds(refIds(rows, "NOTE"));

    Set<Long> postIds = new HashSet<>(refIds(rows, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    Map<Long, PostEntity> posts = bulk(postRepository.findAllByIdIn(postIds), PostEntity::getId);

    Set<Long> userIds =
        posts.values().stream().map(PostEntity::getUserId).collect(Collectors.toSet());
    Map<Long, UserEntity> users = bulk(userRepository.findAllByIdIn(userIds), UserEntity::getId);

    List<RelatedBlockView> out = new ArrayList<>();
    for (CooccurrenceRow row : rows) {
      RelatedBlockView view = resolve(row, posts, highlights, notes, users);
      if (view != null) out.add(view);
    }
    return out;
  }

  public List<KindredCuratorView> kindredCurators(String username, int limit) {
    Optional<UserEntity> me = userRepository.findByUsername(username);
    if (me.isEmpty()) return List.of();
    List<CuratorOverlapRow> rows =
        connectionRepository.findOverlappingCurators(me.get().getId(), clamp(limit));
    if (rows.isEmpty()) return List.of();

    Map<Long, UserEntity> users =
        bulk(
            userRepository.findAllByIdIn(
                rows.stream().map(CuratorOverlapRow::getCuratorId).toList()),
            UserEntity::getId);

    List<KindredCuratorView> out = new ArrayList<>();
    for (CuratorOverlapRow row : rows) {
      UserEntity curator = users.get(row.getCuratorId());
      if (curator == null || curator.getUsername() == null) continue;
      int shared = row.getSharedItems() == null ? 0 : row.getSharedItems().intValue();
      out.add(new KindredCuratorView(PublicAuthorView.from(curator), shared));
    }
    return out;
  }

  private RelatedBlockView resolve(
      CooccurrenceRow row,
      Map<Long, PostEntity> posts,
      Map<Long, PostHighlightEntity> highlights,
      Map<Long, String> notes,
      Map<Long, UserEntity> users) {
    int shared = row.getSharedCount() == null ? 0 : row.getSharedCount().intValue();
    Long refId = row.getRefId();
    return switch (ConnectionBlockType.valueOf(row.getBlockType())) {
      case POST -> {
        PostEntity post = posts.get(refId);
        if (post == null) yield null;
        UserEntity author = users.get(post.getUserId());
        yield new RelatedBlockView(
            "POST",
            refId,
            post.getTitle(),
            post.getExcerpt(),
            post.getSlug(),
            author == null ? null : author.getUsername(),
            null,
            null,
            shared);
      }
      case HIGHLIGHT -> {
        PostHighlightEntity hl = highlights.get(refId);
        if (hl == null) yield null;
        PostEntity post = posts.get(hl.getPostId());
        UserEntity author = post == null ? null : users.get(post.getUserId());
        yield new RelatedBlockView(
            "HIGHLIGHT",
            refId,
            post == null ? null : post.getTitle(),
            null,
            post == null ? null : post.getSlug(),
            author == null ? null : author.getUsername(),
            hl.getQuote(),
            null,
            shared);
      }
      case NOTE -> {
        String body = notes.get(refId);
        if (body == null) yield null;
        yield new RelatedBlockView("NOTE", refId, null, null, null, null, null, body, shared);
      }
    };
  }

  private static int clamp(int limit) {
    if (limit < 1) return 1;
    return Math.min(limit, MAX_LIMIT);
  }

  private static List<Long> refIds(List<CooccurrenceRow> rows, String type) {
    return rows.stream()
        .filter(r -> type.equals(r.getBlockType()))
        .map(CooccurrenceRow::getRefId)
        .distinct()
        .toList();
  }

  private static <T> Map<Long, T> bulk(List<T> items, Function<T, Long> key) {
    return items.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
