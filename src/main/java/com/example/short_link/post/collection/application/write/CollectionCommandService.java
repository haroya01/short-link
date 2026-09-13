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

/** 모든 변경은 주인만 가능하다. 연결 대상의 존재를 검증하며 같은 블록의 재연결은 멱등 처리한다. */
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
    return collectionRepository.save(
        new CollectionEntity(
            cmd.userId(), cmd.title(), cmd.description(), cmd.visibility(), cmd.kind()));
  }

  /** 주어진 ID 순서로 0부터 재배치한다. ID 집합은 컬렉션의 전체 연결과 중복 없이 정확히 일치해야 한다. */
  @Transactional
  public void reorder(Long userId, Long collectionId, List<Long> orderedConnectionIds) {
    CollectionEntity collection = ownedCollection(userId, collectionId);
    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collection.getId());
    Map<Long, CollectionConnectionEntity> byId = new HashMap<>();
    for (CollectionConnectionEntity c : connections) byId.put(c.getId(), c);

    if (orderedConnectionIds.size() != connections.size()
        || !byId.keySet().equals(new HashSet<>(orderedConnectionIds))) {
      throw new PostException(PostErrorCode.COLLECTION_REORDER_MISMATCH, collectionId);
    }

    for (int i = 0; i < orderedConnectionIds.size(); i++) {
      byId.get(orderedConnectionIds.get(i)).reposition(i);
    }
  }

  @Transactional
  public CollectionEntity edit(EditCollectionCommand cmd) {
    CollectionEntity collection = ownedCollection(cmd.userId(), cmd.collectionId());
    collection.edit(cmd.title(), cmd.description(), cmd.visibility());
    return collection;
  }

  /** 기존 연결도 새 {@code why}가 있으면 갱신한다. */
  @Transactional
  public CollectionConnectionEntity connect(ConnectBlockCommand cmd) {
    CollectionEntity collection = ownedCollection(cmd.userId(), cmd.collectionId());
    requireTargetExists(cmd.blockType(), cmd.refId());

    // 삽입 전 연결 목록을 순서 계산과 기존 기여자 알림에 함께 사용한다.
    List<CollectionConnectionEntity> existing =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collection.getId());

    CollectionConnectionEntity already =
        existing.stream()
            .filter(c -> c.getBlockType() == cmd.blockType() && c.getRefId().equals(cmd.refId()))
            .findFirst()
            .orElse(null);
    if (already != null) {
      String rewritten = normalizeWhy(cmd.why());
      if (rewritten != null && !rewritten.isBlank()) already.rewriteWhy(rewritten);
      return already;
    }

    int position =
        existing.stream().mapToInt(CollectionConnectionEntity::getPosition).max().orElse(-1) + 1;
    String why = normalizeWhy(cmd.why());

    CollectionConnectionEntity saved =
        connectionRepository.save(
            new CollectionConnectionEntity(
                collection.getId(), cmd.blockType(), cmd.refId(), why, position));

    publishConnected(collection, cmd, existing);
    return saved;
  }

  /**
   * 새 연결에만 이벤트를 발행한다. 노트 작성자는 큐레이터이므로 수신자에서 제외하며, PATH_GREW는 연결한 작가·큐레이터를 제외한 기존 기여자에게 작가별 한 번만
   * 보낸다. 리스너는 커밋 후 실행하므로 롤백 시 알림도 없다.
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

  private Long authorOf(ConnectionBlockType blockType, Long refId) {
    return switch (blockType) {
      case POST -> postRepository.findById(refId).map(PostEntity::getUserId).orElse(null);
      case HIGHLIGHT ->
          highlightRepository.findById(refId).map(PostHighlightEntity::getUserId).orElse(null);
      case NOTE -> null;
    };
  }

  private Long occasioningPostId(ConnectionBlockType blockType, Long refId) {
    return switch (blockType) {
      case POST -> refId;
      case HIGHLIGHT ->
          highlightRepository.findById(refId).map(PostHighlightEntity::getPostId).orElse(null);
      case NOTE -> null;
    };
  }

  /** 노트는 큐레이터 자신의 것이므로 기여자로 세지 않는다. 연결한 작가와 큐레이터도 제외한다. */
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

  private static String normalizeWhy(String value) {
    if (value == null) return null;
    String stripped = value.strip();
    if (stripped.isEmpty()) return null;
    return stripped.length() > CollectionConnectionEntity.MAX_WHY
        ? stripped.substring(0, CollectionConnectionEntity.MAX_WHY)
        : stripped;
  }
}
