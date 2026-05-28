package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserTwoFactorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserTwoFactorRepository extends JpaRepository<UserTwoFactorEntity, Long> {}
