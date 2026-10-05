package com.example.short_link.link.moderation.domain.repository;

import com.example.short_link.link.moderation.domain.LinkModerationEntity;
import java.util.Optional;

public interface LinkModerationRepository {

  Optional<LinkModerationEntity> findByLinkId(Long linkId);

  void insert(LinkModerationEntity moderation);

  void delete(LinkModerationEntity moderation);
}
