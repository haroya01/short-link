package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.UserTagPrefEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserTagPrefRepository extends JpaRepository<UserTagPrefEntity, Long> {

  List<UserTagPrefEntity> findAllByUserId(Long userId);

  Optional<UserTagPrefEntity> findByUserIdAndTag(Long userId, String tag);
}
