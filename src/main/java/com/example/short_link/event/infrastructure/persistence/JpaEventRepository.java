package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventRepository extends JpaRepository<EventEntity, Long>, EventRepository {

  @Override
  @Modifying
  @Query(
      "UPDATE EventEntity e SET e.registrationCount = e.registrationCount + 1 "
          + "WHERE e.id = :id AND (e.capacity IS NULL OR e.registrationCount < e.capacity)")
  int tryIncrementRegistrationCount(@Param("id") Long id);

  @Override
  @Modifying
  @Query(
      "UPDATE EventEntity e SET e.registrationCount = e.registrationCount - 1 "
          + "WHERE e.id = :id AND e.registrationCount > 0")
  int decrementRegistrationCount(@Param("id") Long id);

  @Override
  @Modifying
  @Query("UPDATE EventEntity e SET e.primaryLinkId = :linkId WHERE e.id = :id")
  void attachPrimaryLink(@Param("id") Long eventId, @Param("linkId") Long linkId);

  @Override
  @Query(
      "SELECT e FROM EventEntity e WHERE e.piiPurgedAt IS NULL "
          + "AND COALESCE(e.endsAt, e.startsAt) < :endedBefore ORDER BY e.id LIMIT :limit")
  List<EventEntity> findPurgeCandidates(
      @Param("endedBefore") Instant endedBefore, @Param("limit") int limit);
}
