package com.example.short_link.link.moderation.infrastructure.persistence;

import com.example.short_link.link.moderation.domain.LinkModerationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkModerationRepository extends JpaRepository<LinkModerationEntity, Long> {}
