package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.repository.ApiKeyRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ApiKeyRepositoryAdapter implements ApiKeyRepository {

  private final JpaApiKeyRepository jpa;

  @Override
  public Optional<ApiKeyEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public ApiKeyEntity save(ApiKeyEntity apiKey) {
    return jpa.save(apiKey);
  }

  @Override
  public Optional<ApiKeyEntity> findByKeyHash(String keyHash) {
    return jpa.findByKeyHash(keyHash);
  }

  @Override
  public List<ApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }
}
