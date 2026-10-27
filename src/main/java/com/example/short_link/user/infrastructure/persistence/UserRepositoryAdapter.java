package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

  private final JpaUserRepository jpa;

  @Override
  public Optional<UserEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<UserEntity> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id);
  }

  @Override
  public List<UserEntity> findAllByIdIn(Collection<Long> ids) {
    return jpa.findAllById(ids);
  }

  @Override
  public UserEntity save(UserEntity user) {
    return jpa.save(user);
  }

  @Override
  public boolean existsById(Long id) {
    return jpa.existsById(id);
  }

  @Override
  public void deleteById(Long id) {
    jpa.deleteById(id);
  }

  @Override
  public Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId) {
    return jpa.findByOauthProviderAndOauthId(oauthProvider, oauthId);
  }

  @Override
  public Optional<UserEntity> findByEmail(String email) {
    return jpa.findByEmail(email);
  }

  @Override
  public Optional<UserEntity> findByUsername(String username) {
    return jpa.findByUsername(username);
  }

  @Override
  public List<UserEntity> findActiveByUsernameIn(Collection<String> usernames) {
    return jpa.findAllByUsernameInAndDeletedAtIsNull(usernames);
  }

  @Override
  public long countByUsernameIsNotNullAndDeletedAtIsNull() {
    return jpa.countByUsernameIsNotNullAndDeletedAtIsNull();
  }

  @Override
  public List<String> findPublicHandles(int page, int size) {
    return jpa
        .findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(PageRequest.of(page, size))
        .stream()
        .map(UserEntity::getUsername)
        .toList();
  }

  @Override
  public List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff) {
    return jpa.findTop200ByDeletedAtBefore(cutoff);
  }
}
