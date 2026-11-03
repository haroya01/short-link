package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventRegistrationRepository
    extends JpaRepository<EventRegistrationEntity, Long>, EventRegistrationRepository {

  String HOLDS_PII =
      "(r.name <> '[purged]' OR r.contact <> CONCAT('purged:', r.id) OR r.answersJson IS NOT NULL)";

  @Override
  @Modifying
  @Query(
      "UPDATE EventRegistrationEntity r SET r.name = '[purged]', "
          + "r.contact = CONCAT('purged:', r.id), r.answersJson = NULL "
          + "WHERE r.eventId = :eventId AND "
          + HOLDS_PII)
  int purgePiiByEventId(@Param("eventId") Long eventId);
}
