package com.example.short_link.link.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {

  boolean existsByShortCode(String shortCode);

  Optional<LinkEntity> findByShortCode(String shortCode);
}
