package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserFeedPrefRepository
    extends JpaRepository<UserFeedPrefEntity, Long>, UserFeedPrefRepository {}
