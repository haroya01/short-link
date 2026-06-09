package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import java.util.Optional;

public interface UserFeedPrefRepository {

  Optional<UserFeedPrefEntity> findByUserId(Long userId);

  UserFeedPrefEntity save(UserFeedPrefEntity pref);
}
