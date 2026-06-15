package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
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
 * 컬렉션 읽기 — 내 컬렉션 목록과 상세(연결된 블록 해석). 상세는 연결의 다형 ref 를 글·하이라이트· 노트 본문으로 일괄 해석(N+1 없이 bulk 맵)하고, 대상이
 * 사라진 연결은 조용히 건너뛴다. PRIVATE 컬렉션은 주인 외엔 존재조차 새지 않게 NOT_FOUND 로 막는다(§0 바깥은 조용히).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionQueryService {

  private final CollectionRepository collectionRepository;
  private final CollectionConnectionRepository connectionRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteRepository noteRepository;
  private final UserRepository userRepository;

  /** Connections in a collection — for echoing a fresh summary after edit. */
  public long connectionCount(Long collectionId) {
    return connectionRepository.countByCollectionId(collectionId);
  }

  /** The viewer's own collections, most recently touched first. */
  public List<CollectionSummaryView> listMine(Long userId) {
    return collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId).stream()
        .map(
            c ->
                new CollectionSummaryView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getVisibility().name(),
                    (int) connectionRepository.countByCollectionId(c.getId()),
                    c.getUpdatedAt()))
        .toList();
  }

  /** Collection detail with resolved blocks. PRIVATE is hidden from non-owners as NOT_FOUND. */
  public CollectionDetailView detail(Long viewerId, Long collectionId) {
    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .filter(c -> c.isVisibleTo(viewerId))
            .orElseThrow(() -> new PostException(PostErrorCode.COLLECTION_NOT_FOUND, collectionId));

    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collectionId);

    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(refIds(connections, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, NoteEntity> notes =
        bulk(noteRepository.findAllByIdIn(refIds(connections, "NOTE")), NoteEntity::getId);

    // 글은 직접 연결(POST)과 하이라이트의 원문(HIGHLIGHT→postId) 둘 다에서 모은다.
    Set<Long> postIds = new HashSet<>(refIds(connections, "POST"));
    highlights.values().forEach(h -> postIds.add(h.getPostId()));
    Map<Long, PostEntity> posts = bulk(postRepository.findAllByIdIn(postIds), PostEntity::getId);

    Set<Long> authorIds =
        posts.values().stream().map(PostEntity::getUserId).collect(Collectors.toSet());
    Map<Long, UserEntity> authors =
        bulk(userRepository.findAllByIdIn(authorIds), UserEntity::getId);

    String curatorUsername =
        userRepository.findById(collection.getOwnerId()).map(UserEntity::getUsername).orElse(null);

    List<ConnectionView> views = new ArrayList<>();
    for (CollectionConnectionEntity c : connections) {
      ConnectionView view =
          switch (c.getBlockType()) {
            case POST -> {
              PostEntity post = posts.get(c.getRefId());
              if (post == null) yield null;
              UserEntity author = authors.get(post.getUserId());
              yield ConnectionView.post(
                  c.getId(),
                  c.getWhy(),
                  c.getCreatedAt(),
                  post.getTitle(),
                  post.getExcerpt(),
                  post.getSlug(),
                  author == null ? null : author.getUsername());
            }
            case HIGHLIGHT -> {
              PostHighlightEntity hl = highlights.get(c.getRefId());
              if (hl == null) yield null;
              PostEntity post = posts.get(hl.getPostId());
              UserEntity author = post == null ? null : authors.get(post.getUserId());
              yield ConnectionView.highlight(
                  c.getId(),
                  c.getWhy(),
                  c.getCreatedAt(),
                  hl.getQuote(),
                  post == null ? null : post.getTitle(),
                  post == null ? null : post.getSlug(),
                  author == null ? null : author.getUsername());
            }
            case NOTE -> {
              NoteEntity note = notes.get(c.getRefId());
              if (note == null) yield null;
              yield ConnectionView.note(c.getId(), c.getWhy(), c.getCreatedAt(), note.getBody());
            }
          };
      if (view != null) views.add(view);
    }

    return new CollectionDetailView(
        collection.getId(),
        collection.getTitle(),
        collection.getDescription(),
        collection.getVisibility().name(),
        curatorUsername,
        views);
  }

  private static List<Long> refIds(List<CollectionConnectionEntity> connections, String type) {
    return connections.stream()
        .filter(c -> c.getBlockType().name().equals(type))
        .map(CollectionConnectionEntity::getRefId)
        .distinct()
        .toList();
  }

  private static <T> Map<Long, T> bulk(List<T> items, Function<T, Long> key) {
    return items.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
