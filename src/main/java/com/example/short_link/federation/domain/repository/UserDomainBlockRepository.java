package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.UserDomainBlockEntity;
import java.util.List;
import java.util.Optional;

public interface UserDomainBlockRepository {

  List<UserDomainBlockEntity> list(Long userId);

  Optional<UserDomainBlockEntity> find(Long userId, String domain);

  boolean blocks(Long userId, String domain);

  UserDomainBlockEntity save(UserDomainBlockEntity block);

  int delete(Long userId, String domain);
}
