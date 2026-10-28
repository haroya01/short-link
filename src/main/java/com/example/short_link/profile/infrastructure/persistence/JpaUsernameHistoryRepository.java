package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUsernameHistoryRepository
    extends JpaRepository<UsernameHistoryEntity, Long>, UsernameHistoryRepository {}
