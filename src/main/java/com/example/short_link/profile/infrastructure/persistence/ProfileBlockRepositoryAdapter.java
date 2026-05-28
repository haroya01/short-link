package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ProfileBlockRepositoryAdapter implements ProfileBlockRepository {

  private final JpaProfileBlockRepository jpa;

  @Override
  public Optional<ProfileBlockEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public ProfileBlockEntity save(ProfileBlockEntity block) {
    return jpa.save(block);
  }

  @Override
  public void delete(ProfileBlockEntity block) {
    jpa.delete(block);
  }

  @Override
  public List<ProfileBlockEntity> findAllByUserIdOrderByProfileOrderAsc(Long userId) {
    return jpa.findAllByUserIdOrderByProfileOrderAsc(userId);
  }

  @Override
  public long countByUserId(Long userId) {
    return jpa.countByUserId(userId);
  }
}
