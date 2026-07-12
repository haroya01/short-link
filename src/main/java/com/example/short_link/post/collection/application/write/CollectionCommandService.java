package com.example.short_link.post.collection.application.write;

import com.example.short_link.common.event.CollectionConnectedEvent;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컬렉션 쓰기 — 만들기 / 블록 잇기(연결) / 연결 끊기 / 컬렉션 삭제. "연결"이 §0의 핵심 동사라 대상(글·하이라이트·노트)이 실제로 존재하는지 검증하고, 같은
 * 블록을 같은 컬렉션에 두 번 잇는 요청은 멱등하게 흘려보낸다(유니크 키와 일관). 모든 변경은 주인만.
 */
@Service
@RequiredArgsConstructor
public class CollectionCommandService {

  private final CollectionRepository collectionRepository;
  private final CollectionConnectionRepository connectionRepository;
  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;
  private final NoteRepository noteRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public CollectionEntity create(CreateCollectionCommand cmd) {
    String title = cmd.title() == null ? "" : cmd.title().strip();
    if (title.isEmpty()) {
      throw new PostException(PostErrorCode.COLLECTION_TITLE_REQUIRED);
    }
    title = clamp(title, CollectionEntity.MAX_TITLE);
    String description = clampNullable(cmd.description(), CollectionEntity.MAX_DESCRIPTION);
    return collectionRepository.save(
        new CollectionEntity(cmd.userId(), title, description, cmd.visibility(), cmd.kind()));
  }

  /**
   * 연결 순서 재배치(주인만) — PATH(reading path)에서 position 이 곧 논증의 흐름이다. 주어진 connectionId 순서대로 position 을
   * 0..n 으로 다시 매긴다. 제공된 id 집합이 이 컬렉션의 연결 집합과 정확히 일치해야 한다(부분/이질 id 거부).
   */
  @Transactional
  public void reorder(Long userId, Long collectionId, List<Long> orderedConnectionIds) {
    CollectionEntity collection = ownedCollection(userId, collectionId);
    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collection.getId());
    Map<Long, CollectionConnectionEntity> byId = new HashMap<>();
    for (CollectionConnectionEntity c : connections) byId.put(c.getId(), c);

    if (orderedConnectionIds.size() != connections.size()
        || !byId.keySet().equals(new HashSet<>(orderedConnectionIds))) {
      // 한 컬렉션의 연결 전체를 빠짐없이·중복 없이 넘겨야 한다(스냅샷 재배치).
      throw new PostException(PostErrorCode.COLLECTION_REORDER_MISMATCH, collectionId);
    }

    for (int i = 0; i < orderedConnectionIds.size(); i++) {
      byId.get(orderedConnectionIds.get(i)).reposition(i);
    }
    // updated_at 을 끌어올려 "최근 손댄 컬렉션"이 목록 위로.
    collection.edit(collection.getTitle(), collection.getDescription(), collection.getVisibility());
  }

  /** 이름·소개·공개 범위 수정(주인만). 제목은 비울 수 없다. */
  @Transactional
  public CollectionEntity edit(EditCollectionCommand cmd) {
    CollectionEntity collection = ownedCollection(cmd.userId(), cmd.collectionId());
    String title = cmd.title() == null ? "" : cmd.title().strip();
    if (title.isEmpty()) {
      throw new PostException(PostErrorCode.COLLECTION_TITLE_REQUIRED);
    }
    collection.edit(
        clamp(title, CollectionEntity.MAX_TITLE),
        clampNullable(cmd.description(), CollectionEntity.MAX_DESCRIPTION),
        cmd.visibility());
    return collection;
  }

  /** 블록을 컬렉션에 잇는다. 이미 이어져 있으면 그대로 둔다(멱등). */
  @Transactional
  public CollectionConnectionEntity connect(ConnectBlockCommand cmd) {
    CollectionEntity collection = ownedCollection(cmd.userId(), cmd.collectionId());
    requireTargetExists(cmd.blockType(), cmd.refId());

    // 새 삽입 전의 길 — 이미 담긴 연결들. 멱등 조기반환·position 계산·PATH_GREW 수신자(기여자) 해석에 모두 재사용해
    // existsBy/findMaxPosition 를 되풀이하지 않는다.
    List<CollectionConnectionEntity> existing =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collection.getId());

    CollectionConnectionEntity already =
        existing.stream()
            .filter(c -> c.getBlockType() == cmd.blockType() && c.getRefId().equals(cmd.refId()))
            .findFirst()
            .orElse(null);
    if (already != null) {
      return already;
    }

    int position =
        existing.stream().mapToInt(CollectionConnectionEntity::getPosition).max().orElse(-1) + 1;
    String why = clampNullable(cmd.why(), CollectionConnectionEntity.MAX_WHY);

    CollectionConnectionEntity saved =
        connectionRepository.save(
            new CollectionConnectionEntity(
                collection.getId(), cmd.blockType(), cmd.refId(), why, position));
    // updated_at 을 끌어올려 "최근 손댄 컬렉션"이 목록 위로 온다.
    collection.edit(collection.getTitle(), collection.getDescription(), collection.getVisibility());

    publishConnected(collection, cmd, existing);
    return saved;
  }

  /**
   * 연결이 실제로 새로 생겼을 때만 그래프 알림 이벤트를 낸다(멱등 재연결은 조용히). 소유 슬라이스(글·하이라이트·노트 리포지토리를 이미 든다)에서 수신자를 모두 해석해
   * 넘긴다: 이어진 글/하이라이트의 작가(CONNECTED 수신자)와, 이미 담겨 있던 블록들의 서로 다른 작가들(PATH_GREW 수신자 — 이어진 작가·큐레이터 제외,
   * 작가별 dedup). 노트는 작가가 곧 큐레이터라 CONNECTED 수신자를 만들지 않고 기여자로도 치지 않는다. 작가 조회는 타입별 한 벌크 쿼리로 N+1 을 피한다.
   * 이벤트는 커밋 후 리스너가 소비하므로 롤백되면 알림도 없다.
   */
  private void publishConnected(
      CollectionEntity collection,
      ConnectBlockCommand cmd,
      List<CollectionConnectionEntity> priorConnections) {
    Long curatorId = cmd.userId();
    Long connectedAuthorId = authorOf(cmd.blockType(), cmd.refId());
    Long connectedPostId = occasioningPostId(cmd.blockType(), cmd.refId());

    List<Long> priorContributors =
        priorContributorAuthorIds(priorConnections, connectedAuthorId, curatorId);

    events.publishEvent(
        new CollectionConnectedEvent(
            curatorId,
            collection.getId(),
            collection.getTitle(),
            connectedPostId,
            connectedAuthorId,
            priorContributors,
            Instant.now()));
  }

  /** 이어진 블록의 작가 — 글·하이라이트는 작성자 user_id, 노트는 작가가 곧 큐레이터라 없음(null). */
  private Long authorOf(ConnectionBlockType blockType, Long refId) {
    return switch (blockType) {
      case POST -> postRepository.findById(refId).map(PostEntity::getUserId).orElse(null);
      case HIGHLIGHT ->
          highlightRepository.findById(refId).map(PostHighlightEntity::getUserId).orElse(null);
      case NOTE -> null;
    };
  }

  /** 미리보기용 계기 글 id — 글은 자기 자신, 하이라이트는 얹힌 글, 노트는 글이 없어 null. */
  private Long occasioningPostId(ConnectionBlockType blockType, Long refId) {
    return switch (blockType) {
      case POST -> refId;
      case HIGHLIGHT ->
          highlightRepository.findById(refId).map(PostHighlightEntity::getPostId).orElse(null);
      case NOTE -> null;
    };
  }

  /**
   * 이미 담겨 있던 블록들의 서로 다른 작가들 — 이어진 작가·큐레이터 제외, 작가별 dedup. 글·하이라이트 ref 를 타입별로 모아 각각 한 벌크 쿼리로 작가를
   * 해석한다(연결 수만큼 단건 조회하지 않음 = N+1 방지). 노트 연결은 기여자로 치지 않는다(작가가 곧 큐레이터).
   */
  private List<Long> priorContributorAuthorIds(
      List<CollectionConnectionEntity> priorConnections, Long connectedAuthorId, Long curatorId) {
    List<Long> postIds = refIdsOf(priorConnections, ConnectionBlockType.POST);
    List<Long> highlightIds = refIdsOf(priorConnections, ConnectionBlockType.HIGHLIGHT);
    if (postIds.isEmpty() && highlightIds.isEmpty()) {
      return List.of();
    }

    Set<Long> authors = new LinkedHashSet<>();
    for (PostEntity post : postRepository.findAllByIdIn(postIds)) {
      authors.add(post.getUserId());
    }
    for (PostHighlightEntity highlight : highlightRepository.findAllByIdIn(highlightIds)) {
      authors.add(highlight.getUserId());
    }
    authors.remove(curatorId);
    if (connectedAuthorId != null) {
      authors.remove(connectedAuthorId);
    }
    return List.copyOf(authors);
  }

  private static List<Long> refIdsOf(
      List<CollectionConnectionEntity> connections, ConnectionBlockType blockType) {
    return connections.stream()
        .filter(c -> c.getBlockType() == blockType)
        .map(CollectionConnectionEntity::getRefId)
        .toList();
  }

  @Transactional
  public void disconnect(Long userId, Long collectionId, Long connectionId) {
    CollectionEntity collection = ownedCollection(userId, collectionId);
    CollectionConnectionEntity connection =
        connectionRepository
            .findById(connectionId)
            .filter(c -> c.getCollectionId().equals(collection.getId()))
            .orElseThrow(() -> new PostException(PostErrorCode.CONNECTION_NOT_FOUND, connectionId));
    connectionRepository.delete(connection);
  }

  @Transactional
  public void deleteCollection(Long userId, Long collectionId) {
    CollectionEntity collection = ownedCollection(userId, collectionId);
    // 연결 행은 FK ON DELETE CASCADE 로 함께 사라진다.
    collectionRepository.delete(collection);
  }

  private CollectionEntity ownedCollection(Long userId, Long collectionId) {
    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .orElseThrow(() -> new PostException(PostErrorCode.COLLECTION_NOT_FOUND, collectionId));
    if (!collection.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.COLLECTION_PERMISSION_DENIED);
    }
    return collection;
  }

  private void requireTargetExists(ConnectionBlockType blockType, Long refId) {
    boolean exists =
        switch (blockType) {
          case POST -> postRepository.findById(refId).isPresent();
          case HIGHLIGHT -> highlightRepository.findById(refId).isPresent();
          case NOTE -> noteRepository.findById(refId).isPresent();
        };
    if (!exists) {
      throw new PostException(PostErrorCode.CONNECTION_TARGET_NOT_FOUND, refId);
    }
  }

  private static String clamp(String value, int max) {
    return value.length() > max ? value.substring(0, max) : value;
  }

  private static String clampNullable(String value, int max) {
    if (value == null) return null;
    String stripped = value.strip();
    if (stripped.isEmpty()) return null;
    return clamp(stripped, max);
  }
}
