package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

  Optional<ApiKeyEntity> findById(Long id);

  ApiKeyEntity save(ApiKeyEntity apiKey);

  Optional<ApiKeyEntity> findByKeyHash(String keyHash);

  List<ApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
