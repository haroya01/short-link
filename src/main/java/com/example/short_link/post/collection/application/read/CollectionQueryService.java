package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.application.read.CollectionSummaryView.Curator;
import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionConnectionRank;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Comparator;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionQueryService {

  private static final Comparator<CollectionEntity> RECENTLY_UPDATED_FIRST =
      Comparator.comparing(CollectionEntity::getUpdatedAt).reversed();

  public static final int PREVIEW_PER_COLLECTION = CollectionContentReader.PREVIEW_PER_COLLECTION;

  private final CollectionRepository collectionRepository;
  private final CollectionConnectionRepository connectionRepository;
  private final CollectionContentReader contentReader;
  private final UserRepository userRepository;

  /** 수정 응답은 저장된 기본 정보와 현재 연결 수만 돌려준다. 목록용 preview·큐레이터·순위는 채우지 않는다. */
  public CollectionSummaryView editedSummary(CollectionEntity saved) {
    long count = connectionRepository.countByCollectionId(saved.getId());
    return CollectionSummaryView.afterWrite(saved, count);
  }

  /** 한 블록을 담은 공개 컬렉션. 각 컬렉션에서의 1-based 위치를 함께 보여준다. */
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
            .sorted(RECENTLY_UPDATED_FIRST)
            .toList();
    if (publicCollections.isEmpty()) return List.of();

    Set<Long> ids =
        publicCollections.stream().map(CollectionEntity::getId).collect(Collectors.toSet());
    Map<Long, Curator> curators = curatorsFor(publicCollections);
    Map<Long, Integer> positions = positionsFor(blockType, refId, ids);
    return publicCollections.stream()
        .map(
            collection ->
                CollectionSummaryView.containingBlock(
                    collection,
                    connectionRepository.countByCollectionId(collection.getId()),
                    curators.getOrDefault(collection.getOwnerId(), Curator.UNKNOWN),
                    positions.get(collection.getId())))
        .toList();
  }

  /** 요청한 블록 순서를 보존하고, 공개 연결이 없는 블록도 빈 목록으로 돌려준다. */
  public Map<Long, List<CollectionSummaryView>> publicCollectionsContainingBatch(
      ConnectionBlockType blockType, List<Long> refIds) {
    List<Long> distinctRefIds = refIds.stream().filter(Objects::nonNull).distinct().toList();
    Map<Long, List<CollectionSummaryView>> result = new LinkedHashMap<>();
    for (Long refId : distinctRefIds) result.put(refId, List.of());
    if (distinctRefIds.isEmpty()) return result;

    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByBlockTypeAndRefIdIn(blockType, distinctRefIds);
    if (connections.isEmpty()) return result;
    Map<Long, CollectionEntity> publicCollections = publicCollectionsFor(connections);
    if (publicCollections.isEmpty()) return result;

    Map<Long, Integer> counts = countsFor(publicCollections.keySet());
    Map<Long, Curator> curators = curatorsFor(List.copyOf(publicCollections.values()));
    Map<BlockInCollection, Integer> positions = positionsFor(blockType, publicCollections.keySet());
    publicCollectionsByRef(connections, publicCollections)
        .forEach(
            (refId, collections) ->
                result.put(
                    refId,
                    collections.stream()
                        .map(
                            collection ->
                                CollectionSummaryView.containingBlock(
                                    collection,
                                    counts.getOrDefault(collection.getId(), 0),
                                    curators.getOrDefault(collection.getOwnerId(), Curator.UNKNOWN),
                                    positions.get(
                                        new BlockInCollection(collection.getId(), refId))))
                        .toList()));
    return result;
  }

  private Map<Long, CollectionEntity> publicCollectionsFor(
      List<CollectionConnectionEntity> connections) {
    Set<Long> ids =
        connections.stream()
            .map(CollectionConnectionEntity::getCollectionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return collectionRepository.findAllByIdIn(ids).stream()
        .filter(collection -> collection.getVisibility() == CollectionVisibility.PUBLIC)
        .collect(Collectors.toMap(CollectionEntity::getId, Function.identity()));
  }

  private Map<Long, Integer> countsFor(Set<Long> collectionIds) {
    return connectionRepository.countByCollectionIdIn(collectionIds).stream()
        .collect(
            Collectors.toMap(
                CollectionConnectionCount::collectionId, count -> (int) count.count()));
  }

  /** 공개 컬렉션만 남기고 같은 블록·컬렉션 쌍의 중복을 제거한다. 같은 수정 시각은 원래 연결 순서를 유지한다. */
  private Map<Long, List<CollectionEntity>> publicCollectionsByRef(
      List<CollectionConnectionEntity> connections, Map<Long, CollectionEntity> publicCollections) {
    Map<Long, Map<Long, CollectionEntity>> distinctByRef = new LinkedHashMap<>();
    for (CollectionConnectionEntity connection : connections) {
      CollectionEntity collection = publicCollections.get(connection.getCollectionId());
      if (collection == null) continue;
      distinctByRef
          .computeIfAbsent(connection.getRefId(), refId -> new LinkedHashMap<>())
          .putIfAbsent(collection.getId(), collection);
    }
    Map<Long, List<CollectionEntity>> sortedByRef = new LinkedHashMap<>();
    distinctByRef.forEach(
        (refId, collections) ->
            sortedByRef.put(
                refId, collections.values().stream().sorted(RECENTLY_UPDATED_FIRST).toList()));
    return sortedByRef;
  }

  private Map<Long, Curator> curatorsFor(List<CollectionEntity> collections) {
    Set<Long> ownerIds =
        collections.stream().map(CollectionEntity::getOwnerId).collect(Collectors.toSet());
    if (ownerIds.isEmpty()) return Map.of();
    return userRepository.findAllByIdIn(ownerIds).stream()
        .collect(
            Collectors.toMap(
                UserEntity::getId, user -> new Curator(user.getUsername(), user.getAvatarUrl())));
  }

  private Map<Long, Integer> positionsFor(
      ConnectionBlockType blockType, Long refId, Set<Long> collectionIds) {
    return connectionRepository
        .findRanksByCollectionIdsAndBlockType(collectionIds, blockType)
        .stream()
        .filter(rank -> refId.equals(rank.refId()))
        .collect(
            Collectors.toMap(
                CollectionConnectionRank::collectionId, CollectionConnectionRank::position));
  }

  private Map<BlockInCollection, Integer> positionsFor(
      ConnectionBlockType blockType, Set<Long> collectionIds) {
    Map<BlockInCollection, Integer> positions = new LinkedHashMap<>();
    for (CollectionConnectionRank rank :
        connectionRepository.findRanksByCollectionIdsAndBlockType(collectionIds, blockType)) {
      positions.put(new BlockInCollection(rank.collectionId(), rank.refId()), rank.position());
    }
    return positions;
  }

  private record BlockInCollection(Long collectionId, Long refId) {}

  public List<CollectionSummaryView> listMine(
      Long userId, ConnectionBlockType blockType, Long refId) {
    List<CollectionEntity> collections =
        collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId);
    Map<Long, List<String>> previews =
        contentReader.previewByCollection(
            collections.stream().map(CollectionEntity::getId).toList(), false);
    Map<Long, Long> connections = existingConnectionsFor(blockType, refId);
    Curator owner =
        userRepository
            .findById(userId)
            .map(user -> new Curator(user.getUsername(), user.getAvatarUrl()))
            .orElse(Curator.UNKNOWN);
    return collections.stream()
        .map(
            collection ->
                CollectionSummaryView.inList(
                    collection,
                    connectionRepository.countByCollectionId(collection.getId()),
                    previews.getOrDefault(collection.getId(), List.of()),
                    owner,
                    connections.get(collection.getId())))
        .toList();
  }

  private Map<Long, Long> existingConnectionsFor(ConnectionBlockType blockType, Long refId) {
    if (blockType == null || refId == null) return Map.of();
    return connectionRepository.findAllByBlockTypeAndRefId(blockType, refId).stream()
        .collect(
            Collectors.toMap(
                CollectionConnectionEntity::getCollectionId,
                CollectionConnectionEntity::getId,
                (first, duplicate) -> first));
  }

  /** 공개 프로필에는 PUBLIC 컬렉션만 노출한다. 없는 사용자명은 빈 목록을 돌려준다. */
  public List<CollectionSummaryView> listPublicByUsername(String username) {
    Optional<UserEntity> user = userRepository.findByUsername(username);
    if (user.isEmpty()) return List.of();
    Curator owner = new Curator(user.get().getUsername(), user.get().getAvatarUrl());
    List<CollectionEntity> collections =
        collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(user.get().getId()).stream()
            .filter(collection -> collection.getVisibility() == CollectionVisibility.PUBLIC)
            .toList();
    Map<Long, List<String>> previews =
        contentReader.previewByCollection(
            collections.stream().map(CollectionEntity::getId).toList(), true);
    return collections.stream()
        .map(
            collection ->
                CollectionSummaryView.inList(
                    collection,
                    connectionRepository.countByCollectionId(collection.getId()),
                    previews.getOrDefault(collection.getId(), List.of()),
                    owner,
                    null))
        .toList();
  }

  /** PRIVATE 컬렉션은 주인 외에는 존재를 노출하지 않는다. */
  public CollectionDetailView detail(Long viewerId, Long collectionId) {
    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .filter(candidate -> candidate.isVisibleTo(viewerId))
            .orElseThrow(() -> new PostException(PostErrorCode.COLLECTION_NOT_FOUND, collectionId));
    List<CollectionConnectionEntity> connections =
        connectionRepository.findAllByCollectionIdOrderByPositionAsc(collectionId);
    List<ConnectionView> views = contentReader.connections(connections);
    String curatorUsername =
        userRepository.findById(collection.getOwnerId()).map(UserEntity::getUsername).orElse(null);
    return new CollectionDetailView(
        collection.getId(),
        collection.getTitle(),
        collection.getDescription(),
        collection.getVisibility().name(),
        collection.getKind().name(),
        curatorUsername,
        views);
  }
}
