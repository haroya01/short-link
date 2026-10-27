package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.UserDomainBlockEntity;
import java.util.List;
import java.util.Optional;

public interface UserDomainBlockRepository {

  List<UserDomainBlockEntity> findByUserIdOrderByDomainAsc(Long userId);

  Optional<UserDomainBlockEntity> findByUserIdAndDomain(Long userId, String domain);

  UserDomainBlockEntity save(UserDomainBlockEntity block);

  int delete(Long userId, String domain);
}
