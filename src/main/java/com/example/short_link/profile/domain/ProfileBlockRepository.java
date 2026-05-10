package com.example.short_link.profile.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileBlockRepository extends JpaRepository<ProfileBlockEntity, Long> {

  List<ProfileBlockEntity> findAllByUserIdOrderByProfileOrderAsc(Long userId);

  long countByUserId(Long userId);
}
