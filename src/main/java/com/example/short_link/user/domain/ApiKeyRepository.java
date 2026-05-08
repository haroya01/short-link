package com.example.short_link.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

  Optional<ApiKeyEntity> findByKeyHash(String keyHash);

  List<ApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
