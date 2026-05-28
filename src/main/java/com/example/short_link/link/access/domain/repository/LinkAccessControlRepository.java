package com.example.short_link.link.access.domain.repository;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import java.util.Optional;

public interface LinkAccessControlRepository {

  Optional<LinkAccessControlEntity> findById(Long id);

  LinkAccessControlEntity save(LinkAccessControlEntity access);
}
