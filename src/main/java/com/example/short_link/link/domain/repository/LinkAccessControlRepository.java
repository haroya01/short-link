package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkAccessControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkAccessControlRepository extends JpaRepository<LinkAccessControlEntity, Long> {}
