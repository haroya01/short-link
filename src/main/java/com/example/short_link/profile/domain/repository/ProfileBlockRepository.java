package com.example.short_link.profile.domain.repository;

import com.example.short_link.profile.domain.*;
import java.util.List;
import java.util.Optional;

public interface ProfileBlockRepository {

  Optional<ProfileBlockEntity> findById(Long id);

  ProfileBlockEntity save(ProfileBlockEntity block);

  void delete(ProfileBlockEntity block);

  List<ProfileBlockEntity> findAllByUserIdOrderByProfileOrderAsc(Long userId);

  long countByUserId(Long userId);
}
