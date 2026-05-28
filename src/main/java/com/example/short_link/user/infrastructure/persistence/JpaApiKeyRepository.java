package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.ApiKeyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

  Optional<ApiKeyEntity> findByKeyHash(String keyHash);

  List<ApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
