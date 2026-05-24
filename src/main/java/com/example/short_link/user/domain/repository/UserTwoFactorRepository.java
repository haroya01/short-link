package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactorEntity, Long> {}
