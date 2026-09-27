package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.email.EmailLeadEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEmailLeadRepository extends JpaRepository<EmailLeadEntity, Long> {

  @Modifying
  @Query(
      value =
          """
          INSERT INTO email_lead (user_id, block_id, email, ip_hash, submitted_at, opted_out)
          VALUES (:userId, :blockId, :email, :ipHash, :submittedAt, :optedOut)
          ON DUPLICATE KEY UPDATE id = id
          """,
      nativeQuery = true)
  void insertIfAbsent(
      @Param("userId") Long userId,
      @Param("blockId") Long blockId,
      @Param("email") String email,
      @Param("ipHash") String ipHash,
      @Param("submittedAt") Instant submittedAt,
      @Param("optedOut") boolean optedOut);

  List<EmailLeadEntity> findAllByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

  List<EmailLeadEntity> findAllByUserIdAndOptedOutFalseOrderBySubmittedAtDesc(
      Long userId, Pageable pageable);

  long countByUserId(Long userId);

  boolean existsByBlockIdAndEmail(Long blockId, String email);

  long countByBlockIdAndSubmittedAtAfter(Long blockId, Instant after);

  long countByIpHashAndSubmittedAtAfter(String ipHash, Instant after);
}
