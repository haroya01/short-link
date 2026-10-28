package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProfileBlockRepository
    extends JpaRepository<ProfileBlockEntity, Long>, ProfileBlockRepository {}
