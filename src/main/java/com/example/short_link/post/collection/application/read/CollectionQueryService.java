package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionConnectionRank;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  /**
   * "이 문장이 속한 길/컬렉션" — 이 블록(하이라이트 등)을 담은 *공개* 컬렉션들(최근순). 비공개·링크공유는 빼서 누구나(미로그인 포함) 안전히 본다. A 척추의 발견
   * 고리 — 한 문장에서 그것이 엮인 길들로. 각 컬렉션엔 소유자(큐레이터)와 이 블록이 그 길에서 몇 번째인지(1-based position, 분모는 count)를 실어
   * "카테고리"가 아니라 "@큐레이터의 길 · N편 중 M번째"로 읽히게 한다.
   */
  public List<CollectionSummaryView> publicCollectionsContaining(
      ConnectionBlockType blockType, Long refId) {
    List<Long> collectionIds =
        connectionRepository.findAllByBlockTypeAndRefId(blockType, refId).stream()
            .map(CollectionConnectionEntity::getCollectionId)
            .distinct()
            .toList();
    List<CollectionEntity> publicCollections =
        collectionIds.stream()
            .map(collectionRepository::findById)
            .flatMap(Optional::stream)
            .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC)
            .sorted(Comparator.comparing(CollectionEntity::getUpdatedAt).reversed())
            .toList();
    if (publicCollections.isEmpty()) return List.of();

    Set<Long> ids =
        publicCollections.stream().map(CollectionEntity::getId).collect(Collectors.toSet());
    Map<Long, Curator> curators = curatorsFor(publicCollections);
    Map<Long, Integer> positionByCollection = positionsFor(blockType, refId, ids);

    return publicCollections.stream()
        .map(
            c ->
                new CollectionSummaryView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getVisibility().name(),
                    c.getKind().name(),
                    (int) connectionRepository.countByCollectionId(c.getId()),
                    c.getUpdatedAt(),
                    List.of(),
                    curators.getOrDefault(c.getOwnerId(), Curator.EMPTY).username(),
                    curators.getOrDefault(c.getOwnerId(), Curator.EMPTY).avatarUrl(),
                    positionByCollection.get(c.getId()),
                    null))
        .toList();
  }

  /** 큐레이터 한 줄 — 컬렉션 소유자의 표시 핸들·아바타. 없는 유저는 EMPTY(둘 다 null). */
  private record Curator(String username, String avatarUrl) {
    static final Curator EMPTY = new Curator(null, null);
  }

  /** 컬렉션들의 소유자(큐레이터)를 한 쿼리로(N+1 방지) — ownerId → 핸들·아바타. */
  private Map<Long, Curator> curatorsFor(List<CollectionEntity> collections) {
    Set<Long> ownerIds =
        collections.stream().map(CollectionEntity::getOwnerId).collect(Collectors.toSet());
    if (ownerIds.isEmpty()) return Map.of();
    return userRepository.findAllByIdIn(ownerIds).stream()
        .collect(
            Collectors.toMap(
                UserEntity::getId, u -> new Curator(u.getUsername(), u.getAvatarUrl())));
  }

  /** 컬렉션들 안에서 이 (blockType, refId) 의 1-based position 을 한 쿼리로 — collectionId → position. */
  private Map<Long, Integer> positionsFor(
      ConnectionBlockType blockType, Long refId, Set<Long> collectionIds) {
    return connectionRepository
        .findRanksByCollectionIdsAndBlockType(collectionIds, blockType)
        .stream()
        .filter(r -> refId.equals(r.refId()))
        .collect(
            Collectors.toMap(
                CollectionConnectionRank::collectionId, CollectionConnectionRank::position));
  }

  /**
   * "이 글들이 각각 속한 길" — 여러 블록(피드가 보이는 카드 id 들)을 담은 *공개* 컬렉션들을 refId 별로(각 최근순). 단건 {@link
   * #publicCollectionsContaining} 을 카드 수만큼 부르지 않게 한 올로 묶은 벌크판: 역연결·컬렉션·count 를 각각 한 쿼리로만 친다(N+1
   * 없음). 빈/없는 refId 는 빈 목록으로 채워 돌려준다(응답에 요청한 모든 refId 가 있게).
   */
  public Map<Long, List<CollectionSummaryView>> publicCollectionsContainingBatch(
      ConnectionBlockType blockType, List<Long> refIds) {
    List<Long> distinctRefIds = refIds.stream().filter(Objects::nonNull).distinct().toList();
    // 모든 refId 를 빈 목록으로 먼저 채운다 — 요청한 카드가 응답에서 빠지지 않게(§0 조용히: 없으면 빈 올).
    Map<Long, List<CollectionSummaryView>> result = new LinkedHashMap<>();
    for (Long refId : distinctRefIds) result.put(refId, List.of());
    if (distinctRefIds.isEmpty()) return result;

    // 1) 역연결 한 쿼리 — 이 블록들을 담은 모든 연결. refId → 담은 컬렉션 id 들(중복 제거).
    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByBlockTypeAndRefIdIn(blockType, distinctRefIds);
    if (connections.isEmpty()) return result;

    // 2) 컬렉션 한 쿼리 — 등장한 컬렉션들을 벌크로 받아 PUBLIC 만 남긴다.
    Set<Long> allCollectionIds =
        connections.stream()
            .map(CollectionConnectionEntity::getCollectionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<Long, CollectionEntity> publicById =
        collectionRepository.findAllByIdIn(allCollectionIds).stream()
            .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC)
            .collect(Collectors.toMap(CollectionEntity::getId, Function.identity()));
    if (publicById.isEmpty()) return result;

    // 3) count 한 쿼리 — 공개 컬렉션들의 담긴 수를 group-by 로.
    Map<Long, Integer> countById =
        connectionRepository.countByCollectionIdIn(publicById.keySet()).stream()
            .collect(
                Collectors.toMap(CollectionConnectionCount::collectionId, c -> (int) c.count()));

    // 4) 큐레이터 한 쿼리 — 공개 컬렉션 소유자들을 벌크로(ownerId → 핸들·아바타). N+1 방지.
    Map<Long, Curator> curators = curatorsFor(List.copyOf(publicById.values()));

    // 5) 순위 한 쿼리 — 공개 컬렉션들 안 이 블록 타입 연결의 1-based position 을 (collectionId, refId) 키로. N+1 방지.
    Map<Long, Map<Long, Integer>> positionByCollectionAndRef = new LinkedHashMap<>();
    for (CollectionConnectionRank rank :
        connectionRepository.findRanksByCollectionIdsAndBlockType(publicById.keySet(), blockType)) {
      positionByCollectionAndRef
          .computeIfAbsent(rank.collectionId(), k -> new LinkedHashMap<>())
          .put(rank.refId(), rank.position());
    }

    // refId 별로 공개 컬렉션을 모아 요약 뷰로(최근순). 컬렉션 조회는 모두 메모리 맵 — 추가 쿼리 없음.
    Map<Long, List<CollectionEntity>> collectionsByRef = new LinkedHashMap<>();
    for (CollectionConnectionEntity conn : connections) {
      CollectionEntity collection = publicById.get(conn.getCollectionId());
      if (collection == null) continue; // 비공개·사라진 컬렉션은 건너뛴다.
      List<CollectionEntity> bucket =
          collectionsByRef.computeIfAbsent(conn.getRefId(), k -> new ArrayList<>());
      if (bucket.stream().noneMatch(c -> c.getId().equals(collection.getId()))) {
        bucket.add(collection); // 같은 컬렉션이 한 글에 두 번 세지 않게(다형 refId 중복 방지).
      }
    }

    collectionsByRef.forEach(
        (refId, collections) ->
            result.put(
                refId,
                collections.stream()
                    .sorted(Comparator.comparing(CollectionEntity::getUpdatedAt).reversed())
                    .map(
                        c ->
                            new CollectionSummaryView(
                                c.getId(),
                                c.getTitle(),
                                c.getDescription(),
                                c.getVisibility().name(),
                                c.getKind().name(),
                                countById.getOrDefault(c.getId(), 0),
                                c.getUpdatedAt(),
                                List.of(),
                                curators.getOrDefault(c.getOwnerId(), Curator.EMPTY).username(),
                                curators.getOrDefault(c.getOwnerId(), Curator.EMPTY).avatarUrl(),
                                positionByCollectionAndRef
                                    .getOrDefault(c.getId(), Map.of())
                                    .get(refId),
                                null))
                    .toList()));
    return result;
  }

  public static final int PREVIEW_PER_COLLECTION = 2;
  private static final int PREVIEW_LABEL_MAX = 40;

  /**
   * The viewer's own collections, most recently touched first, each with a recent-item preview.
   *
   * <p>연결 시트가 특정 블록(blockType×refId)을 기준으로 물으면 각 컬렉션에 그 블록의 기존 연결 PK 를 함께 싣는다 — "이미 담김"을 표시해, 같은
   * 컬렉션에 다시 이으며 새로 쓴 "왜"를 조용히 잃는 사고를 구조적으로 막는다. 블록 없이(null) 부르면 그냥 내 목록이다.
   */
  public List<CollectionSummaryView> listMine(
      Long userId, ConnectionBlockType blockType, Long refId) {
    List<CollectionEntity> collections =
        collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId);
    Map<Long, List<String>> previews =
        previewByCollection(collections.stream().map(CollectionEntity::getId).toList());
    Map<Long, Long> connectionByCollection =
        (blockType == null || refId == null)
            ? Map.of()
            : connectionRepository.findAllByBlockTypeAndRefId(blockType, refId).stream()
                .collect(
                    Collectors.toMap(
                        CollectionConnectionEntity::getCollectionId,
                        CollectionConnectionEntity::getId,
                        (a, b) -> a));
    Curator owner =
        userRepository
            .findById(userId)
            .map(u -> new Curator(u.getUsername(), u.getAvatarUrl()))
            .orElse(Curator.EMPTY);
    return collections.stream()
        .map(
            c ->
                new CollectionSummaryView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getVisibility().name(),
                    c.getKind().name(),
                    (int) connectionRepository.countByCollectionId(c.getId()),
                    c.getUpdatedAt(),
                    previews.getOrDefault(c.getId(), List.of()),
                    owner.username(),
                    owner.avatarUrl(),
                    null, // 대상 글이 없는 목록판 — position 없음.
                    connectionByCollection.get(c.getId())))
        .toList();
  }

  /**
   * A curator's PUBLIC collections, most recently touched first — the public facet of their
   * identity (연결 그래프의 "이 큐레이터의 다른 길들"). 비공개·링크공유는 빼서 누구나(미로그인 포함) 안전히 본다. 없는 핸들은 빈 목록 — 존재 여부가 새지
   * 않게(§0 바깥은 조용히).
   */
  public List<CollectionSummaryView> listPublicByUsername(String username) {
    Optional<UserEntity> user = userRepository.findByUsername(username);
    if (user.isEmpty()) return List.of();
    Curator owner = new Curator(user.get().getUsername(), user.get().getAvatarUrl());
    List<CollectionEntity> collections =
        collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(user.get().getId()).stream()
            .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC)
            .toList();
    Map<Long, List<String>> previews =
        previewByCollection(collections.stream().map(CollectionEntity::getId).toList());
    return collections.stream()
        .map(
            c ->
                new CollectionSummaryView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getVisibility().name(),
                    c.getKind().name(),
                    (int) connectionRepository.countByCollectionId(c.getId()),
                    c.getUpdatedAt(),
                    previews.getOrDefault(c.getId(), List.of()),
                    owner.username(),
                    owner.avatarUrl(),
                    null, // 대상 글이 없는 목록판 — position 없음.
                    null))
        .toList();
  }

  /** 컬렉션마다 최근 항목 라벨 몇 개 — "안에 뭐가 들었는지" 떠올리게(어디 넣을지 결정 보조). */
  private Map<Long, List<String>> previewByCollection(List<Long> collectionIds) {
    if (collectionIds.isEmpty()) return Map.of();

    // 최신순(position desc)으로 받아 컬렉션별 상위 N 만 남긴다.
    Map<Long, List<CollectionConnectionEntity>> topByCollection = new LinkedHashMap<>();
    for (CollectionConnectionEntity c :
        connectionRepository.findAllByCollectionIdInOrderByPositionDesc(collectionIds)) {
      List<CollectionConnectionEntity> bucket =
          topByCollection.computeIfAbsent(c.getCollectionId(), k -> new ArrayList<>());
      if (bucket.size() < PREVIEW_PER_COLLECTION) bucket.add(c);
    }

    List<CollectionConnectionEntity> top =
        topByCollection.values().stream().flatMap(List::stream).toList();
    Map<Long, PostEntity> posts =
        bulk(postRepository.findAllByIdIn(previewRefIds(top, "POST")), PostEntity::getId);
    Map<Long, PostHighlightEntity> highlights =
        bulk(
            highlightRepository.findAllByIdIn(previewRefIds(top, "HIGHLIGHT")),
            PostHighlightEntity::getId);
    Map<Long, NoteEntity> notes =
        bulk(noteRepository.findAllByIdIn(previewRefIds(top, "NOTE")), NoteEntity::getId);

    Map<Long, List<String>> result = new LinkedHashMap<>();
    topByCollection.forEach(
        (collectionId, conns) -> {
          List<String> labels = new ArrayList<>();
          for (CollectionConnectionEntity c : conns) {
            String label = previewLabel(c, posts, highlights, notes);
            if (label != null) labels.add(label);
          }
          result.put(collectionId, labels);
        });
    return result;
  }

  private static String previewLabel(
      CollectionConnectionEntity c,
      Map<Long, PostEntity> posts,
      Map<Long, PostHighlightEntity> highlights,
      Map<Long, NoteEntity> notes) {
    return switch (c.getBlockType()) {
      case POST -> {
        PostEntity post = posts.get(c.getRefId());
        yield post == null ? null : clampLabel(post.getTitle());
      }
      case HIGHLIGHT -> {
        PostHighlightEntity hl = highlights.get(c.getRefId());
        yield hl == null ? null : clampLabel(hl.getQuote());
      }
      case NOTE -> {
        NoteEntity note = notes.get(c.getRefId());
        yield note == null ? null : clampLabel(note.getBody());
      }
    };
  }

  private static List<Long> previewRefIds(List<CollectionConnectionEntity> conns, String type) {
    return conns.stream()
        .filter(c -> c.getBlockType().name().equals(type))
        .map(CollectionConnectionEntity::getRefId)
        .distinct()
        .toList();
  }

  private static String clampLabel(String s) {
    String trimmed = s.strip();
    return trimmed.length() > PREVIEW_LABEL_MAX
        ? trimmed.substring(0, PREVIEW_LABEL_MAX) + "…"
        : trimmed;
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
        collection.getKind().name(),
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
