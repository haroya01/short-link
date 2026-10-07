package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteScheduleEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteScheduleRepository extends JpaRepository<NoteScheduleEntity, Long> {

  List<NoteScheduleEntity> findByUserIdOrderByPublishAtAsc(Long userId);

  Optional<NoteScheduleEntity> findByIdAndUserId(Long id, Long userId);

  @Query(
      "select coalesce(sum(case when s.failure is null then 1 else 0 end), 0),"
          + " coalesce(sum(case when s.publishAt >= :from and s.publishAt < :to then 1 else 0 end), 0)"
          + " from NoteScheduleEntity s where s.userId = :userId")
  List<Object[]> load(
      @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

  @Modifying
  @Query("delete from NoteScheduleEntity s where s.userId = :userId")
  int deleteAllForUser(@Param("userId") Long userId);

  @Query(
      "select s.id from NoteScheduleEntity s where s.failure is null and s.publishAt <= :now"
          + " order by s.publishAt")
  List<Long> findDue(@Param("now") Instant now, Pageable page);
}
