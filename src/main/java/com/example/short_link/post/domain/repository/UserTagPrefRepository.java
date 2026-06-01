package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.UserTagPrefEntity;
import java.util.List;
import java.util.Optional;

public interface UserTagPrefRepository {

  List<UserTagPrefEntity> findAllByUserId(Long userId);

  Optional<UserTagPrefEntity> findByUserIdAndTag(Long userId, String tag);

  UserTagPrefEntity save(UserTagPrefEntity pref);

  void delete(UserTagPrefEntity pref);
}
