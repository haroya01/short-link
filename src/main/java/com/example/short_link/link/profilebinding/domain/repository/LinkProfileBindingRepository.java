package com.example.short_link.link.profilebinding.domain.repository;

import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import java.util.Optional;

public interface LinkProfileBindingRepository {

  Optional<LinkProfileBindingEntity> findById(Long id);

  LinkProfileBindingEntity save(LinkProfileBindingEntity binding);
}
