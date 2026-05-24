package com.example.short_link.profile.domain.email;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLeadRepository extends JpaRepository<EmailLeadEntity, Long> {

  List<EmailLeadEntity> findAllByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

  List<EmailLeadEntity> findAllByUserIdAndOptedOutFalseOrderBySubmittedAtDesc(
      Long userId, Pageable pageable);

  long countByUserId(Long userId);

  boolean existsByBlockIdAndEmail(Long blockId, String email);

  long countByBlockIdAndSubmittedAtAfter(Long blockId, Instant after);

  long countByIpHashAndSubmittedAtAfter(String ipHash, Instant after);
}
