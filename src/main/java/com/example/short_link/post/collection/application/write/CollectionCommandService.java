package com.example.short_link.post.collection.application.write;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

    if (connectionRepository.existsByCollectionIdAndBlockTypeAndRefId(
        collection.getId(), cmd.blockType(), cmd.refId())) {
      return connectionRepository
          .findAllByCollectionIdOrderByPositionAsc(collection.getId())
          .stream()
          .filter(c -> c.getBlockType() == cmd.blockType() && c.getRefId().equals(cmd.refId()))
          .findFirst()
          .orElseThrow();
    }

    Integer maxPosition = connectionRepository.findMaxPositionByCollectionId(collection.getId());
    int position = maxPosition == null ? 0 : maxPosition + 1;
    String why = clampNullable(cmd.why(), CollectionConnectionEntity.MAX_WHY);

    CollectionConnectionEntity saved =
        connectionRepository.save(
            new CollectionConnectionEntity(
                collection.getId(), cmd.blockType(), cmd.refId(), why, position));
    // updated_at 을 끌어올려 "최근 손댄 컬렉션"이 목록 위로 온다.
    collection.edit(collection.getTitle(), collection.getDescription(), collection.getVisibility());
    return saved;
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
