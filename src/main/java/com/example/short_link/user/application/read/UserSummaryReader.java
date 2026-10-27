package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSummaryReader {

  private final UserRepository users;

  public Optional<UserSummary> find(Long id) {
    return users.findById(id).map(UserSummaryReader::summary);
  }

  public List<UserSummary> findAll(Collection<Long> ids) {
    return users.findAllByIdIn(ids).stream().map(UserSummaryReader::summary).toList();
  }

  public Optional<UserSummary> findByUsername(String username) {
    return users.findByUsername(username).map(UserSummaryReader::summary);
  }

  public Optional<UserSummary> findActiveByUsername(String username) {
    return users
        .findByUsername(username)
        .filter(UserSummaryReader::active)
        .map(UserSummaryReader::summary);
  }

  private static boolean active(UserEntity user) {
    return !user.isDeleted();
  }

  private static UserSummary summary(UserEntity user) {
    return new UserSummary(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getBio());
  }
}
