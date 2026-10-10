package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionConnectionRank;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CooccurrenceRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CuratorOverlapRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CollectionConnectionRepositoryAdapter implements CollectionConnectionRepository {

  private static final String PUBLIC_CONNECTIONS =
      "SELECT c.id AS connection_id, c.block_type, c.ref_id, c.why, c.created_at,"
          + " col.id AS collection_id, col.title, col.kind, col.owner_id"
          + " FROM collection_connection c JOIN collection col ON col.id = c.collection_id"
          + " WHERE col.visibility = 'PUBLIC'";

  private static final String HEARD =
      HeardSql.heard("col.owner_id")
          + " AND (c.block_type <> 'POST' OR EXISTS (SELECT 1 FROM posts rp"
          + " WHERE rp.id = c.ref_id"
          + HeardSql.heard("rp.user_id")
          + "))"
          + " AND (c.block_type <> 'HIGHLIGHT' OR EXISTS (SELECT 1 FROM post_highlight rh"
          + " JOIN posts rhp ON rhp.id = rh.post_id WHERE rh.id = c.ref_id"
          + HeardSql.heard("rh.user_id")
          + HeardSql.heard("rhp.user_id")
          + "))"
          + " AND (c.block_type <> 'NOTE' OR EXISTS (SELECT 1 FROM note rn"
          + " WHERE rn.id = c.ref_id"
          + HeardSql.heard("rn.user_id")
          + "))"
          + " ORDER BY c.created_at DESC";

  private final JpaCollectionConnectionRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public CollectionConnectionEntity save(CollectionConnectionEntity connection) {
    return jpa.save(connection);
  }

  @Override
  public Optional<CollectionConnectionEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(CollectionConnectionEntity connection) {
    jpa.delete(connection);
  }

  @Override
  public List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(
      Long collectionId) {
    return jpa.findAllByCollectionIdOrderByPositionAsc(collectionId);
  }

  @Override
  public List<CollectionConnectionEntity> findAllByCollectionIdInOrderByPositionDesc(
      Collection<Long> collectionIds) {
    if (collectionIds.isEmpty()) return List.of();
    return jpa.findAllByCollectionIdInOrderByPositionDesc(collectionIds);
  }

  @Override
  public List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, Long viewerId, int page, int size) {
    if (ownerIds.isEmpty()) return List.of();
    return discoverRows(
        em.createNativeQuery(PUBLIC_CONNECTIONS + " AND col.owner_id IN (:ownerIds)" + HEARD)
            .setParameter("ownerIds", ownerIds),
        viewerId,
        page,
        size);
  }

  @Override
  public List<DiscoverConnectionRow> findRecentPublicConnections(
      Long viewerId, int page, int size) {
    return discoverRows(em.createNativeQuery(PUBLIC_CONNECTIONS + HEARD), viewerId, page, size);
  }

  @SuppressWarnings("unchecked")
  private static List<DiscoverConnectionRow> discoverRows(
      Query query, Long viewerId, int page, int size) {
    List<Object[]> rows =
        query
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", Instant.now())
            .setFirstResult(page * size)
            .setMaxResults(size)
            .unwrap(NativeQuery.class)
            .addScalar("connection_id", Long.class)
            .addScalar("block_type", String.class)
            .addScalar("ref_id", Long.class)
            .addScalar("why", String.class)
            .addScalar("created_at", Instant.class)
            .addScalar("collection_id", Long.class)
            .addScalar("title", String.class)
            .addScalar("kind", String.class)
            .addScalar("owner_id", Long.class)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new DiscoverConnectionRow(
                    (Long) row[0],
                    ConnectionBlockType.valueOf((String) row[1]),
                    (Long) row[2],
                    (String) row[3],
                    (Instant) row[4],
                    (Long) row[5],
                    (String) row[6],
                    row[7] == null ? null : CollectionKind.valueOf((String) row[7]),
                    (Long) row[8]))
        .toList();
  }

  @Override
  public long countByCollectionId(Long collectionId) {
    return jpa.countByCollectionId(collectionId);
  }

  @Override
  public List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId) {
    return jpa.findAllByBlockTypeAndRefId(blockType, refId);
  }

  @Override
  public List<CollectionConnectionEntity> findAllByBlockTypeAndRefIdIn(
      ConnectionBlockType blockType, Collection<Long> refIds) {
    if (refIds.isEmpty()) return List.of();
    return jpa.findAllByBlockTypeAndRefIdIn(blockType, refIds);
  }

  @Override
  public List<CollectionConnectionCount> countByCollectionIdIn(Collection<Long> collectionIds) {
    if (collectionIds.isEmpty()) return List.of();
    return jpa.countByCollectionIdIn(collectionIds);
  }

  @Override
  public List<CollectionConnectionRank> findRanksByCollectionIdsAndBlockType(
      Collection<Long> collectionIds, ConnectionBlockType blockType) {
    if (collectionIds.isEmpty()) return List.of();
    return jpa.findRanksByCollectionIdInAndBlockType(collectionIds, blockType.name()).stream()
        .map(r -> new CollectionConnectionRank(r.getCollectionId(), r.getRefId(), r.getPosition()))
        .toList();
  }

  @Override
  public List<CooccurrenceRow> findCooccurring(
      ConnectionBlockType blockType, Long refId, int limit) {
    return jpa.findCooccurring(blockType.name(), refId, limit);
  }

  @Override
  public List<CuratorOverlapRow> findOverlappingCurators(Long ownerId, int limit) {
    return jpa.findOverlappingCurators(ownerId, limit);
  }
}
