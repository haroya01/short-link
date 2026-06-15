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
        new CollectionEntity(cmd.userId(), title, description, cmd.visibility()));
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
