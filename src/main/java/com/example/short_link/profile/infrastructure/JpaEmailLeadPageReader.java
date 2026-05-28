package com.example.short_link.profile.infrastructure;

import com.example.short_link.profile.application.email.EmailLeadPageReader;
import com.example.short_link.profile.domain.email.EmailLeadEntity;
import com.example.short_link.profile.infrastructure.persistence.JpaEmailLeadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaEmailLeadPageReader implements EmailLeadPageReader {

  private final JpaEmailLeadRepository repository;

  @Override
  public List<EmailLeadEntity> findByUser(Long userId, int page, int size) {
    return repository.findAllByUserIdOrderBySubmittedAtDesc(userId, PageRequest.of(page, size));
  }

  @Override
  public List<EmailLeadEntity> findActiveByUser(Long userId, int page, int size) {
    return repository.findAllByUserIdAndOptedOutFalseOrderBySubmittedAtDesc(
        userId, PageRequest.of(page, size));
  }
}
