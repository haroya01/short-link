package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.email.EmailLeadEntity;
import com.example.short_link.profile.domain.email.EmailLeadRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class EmailLeadRepositoryAdapter implements EmailLeadRepository {

  private final JpaEmailLeadRepository jpa;

  @Override
  public Optional<EmailLeadEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public EmailLeadEntity save(EmailLeadEntity lead) {
    return jpa.save(lead);
  }

  @Override
  public void delete(EmailLeadEntity lead) {
    jpa.delete(lead);
  }

  @Override
  public long countByUserId(Long userId) {
    return jpa.countByUserId(userId);
  }

  @Override
  public boolean existsByBlockIdAndEmail(Long blockId, String email) {
    return jpa.existsByBlockIdAndEmail(blockId, email);
  }

  @Override
  public long countByBlockIdAndSubmittedAtAfter(Long blockId, Instant after) {
    return jpa.countByBlockIdAndSubmittedAtAfter(blockId, after);
  }

  @Override
  public long countByIpHashAndSubmittedAtAfter(String ipHash, Instant after) {
    return jpa.countByIpHashAndSubmittedAtAfter(ipHash, after);
  }
}
