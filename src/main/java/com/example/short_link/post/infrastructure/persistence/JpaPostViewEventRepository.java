package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostViewEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPostViewEventRepository extends JpaRepository<PostViewEventEntity, Long> {}
