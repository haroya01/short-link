package com.example.short_link.link.og.domain.repository;

import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import java.util.Optional;

public interface LinkOgMetadataRepository {

  Optional<LinkOgMetadataEntity> findById(Long id);

  LinkOgMetadataEntity save(LinkOgMetadataEntity metadata);
}
