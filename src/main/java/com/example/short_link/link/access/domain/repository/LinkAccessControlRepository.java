package com.example.short_link.link.access.domain.repository;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkAccessControlRepository extends JpaRepository<LinkAccessControlEntity, Long> {}
