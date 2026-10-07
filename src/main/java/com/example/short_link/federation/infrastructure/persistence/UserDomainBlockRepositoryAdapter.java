package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.UserDomainBlockEntity;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserDomainBlockRepositoryAdapter implements UserDomainBlockRepository {

  private final JpaUserDomainBlockRepository jpa;

  @Override
  public List<UserDomainBlockEntity> list(Long userId) {
    return jpa.findByUserIdOrderByDomainAsc(userId);
  }

  @Override
  public Optional<UserDomainBlockEntity> find(Long userId, String domain) {
    return jpa.findByUserIdAndDomain(userId, domain);
  }

  @Override
  public boolean blocks(Long userId, String domain) {
    return userId != null && domain != null && jpa.existsByUserIdAndDomain(userId, domain);
  }

  @Override
  public UserDomainBlockEntity save(UserDomainBlockEntity block) {
    return jpa.save(block);
  }

  @Override
  public int delete(Long userId, String domain) {
    return jpa.deleteBlock(userId, domain);
  }
}
