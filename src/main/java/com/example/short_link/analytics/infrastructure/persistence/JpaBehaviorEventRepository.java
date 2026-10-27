package com.example.short_link.analytics.infrastructure.persistence;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBehaviorEventRepository
    extends JpaRepository<BehaviorEventEntity, Long>, BehaviorEventRepository {}
