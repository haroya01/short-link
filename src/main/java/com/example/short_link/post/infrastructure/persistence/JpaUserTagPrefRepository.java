package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserTagPrefRepository
    extends JpaRepository<UserTagPrefEntity, Long>, UserTagPrefRepository {}
