package com.example.short_link.link;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {

  boolean existsByShortCode(String shortCode);
}
