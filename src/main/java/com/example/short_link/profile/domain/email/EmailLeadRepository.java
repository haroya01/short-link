package com.example.short_link.profile.domain.email;

import java.time.Instant;
import java.util.Optional;

public interface EmailLeadRepository {

  Optional<EmailLeadEntity> findById(Long id);

  EmailLeadEntity save(EmailLeadEntity lead);

  void delete(EmailLeadEntity lead);

  long countByUserId(Long userId);

  boolean existsByBlockIdAndEmail(Long blockId, String email);

  long countByBlockIdAndSubmittedAtAfter(Long blockId, Instant after);

  long countByIpHashAndSubmittedAtAfter(String ipHash, Instant after);
}
