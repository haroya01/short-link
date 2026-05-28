package com.example.short_link.profile.infrastructure;

import com.example.short_link.profile.application.read.PublicHandleReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.infrastructure.persistence.JpaUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaPublicHandleReader implements PublicHandleReader {

  private final JpaUserRepository userRepository;

  @Override
  public List<String> findPage(int page, int size) {
    return userRepository
        .findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(PageRequest.of(page, size))
        .stream()
        .map(UserEntity::getUsername)
        .toList();
  }
}
