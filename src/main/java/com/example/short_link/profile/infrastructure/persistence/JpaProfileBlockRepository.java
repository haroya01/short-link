package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProfileBlockRepository extends JpaRepository<ProfileBlockEntity, Long> {

  List<ProfileBlockEntity> findAllByUserIdOrderByProfileOrderAsc(Long userId);

  long countByUserId(Long userId);
}
