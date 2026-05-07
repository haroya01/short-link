package com.example.short_link.link.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {

  boolean existsByShortCode(String shortCode);

  Optional<LinkEntity> findByShortCode(String shortCode);

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  long countByCreatedAtAfter(Instant since);

  long countByUserIdIsNull();

  long countByExpiresAtBefore(Instant when);
}
