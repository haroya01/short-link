package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.UserDomainBlockEntity;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserDomainBlockRepository
    extends JpaRepository<UserDomainBlockEntity, Long>, UserDomainBlockRepository {

  @Override
  @Modifying
  @Query("delete from UserDomainBlockEntity b where b.userId = :userId and b.domain = :domain")
  int delete(@Param("userId") Long userId, @Param("domain") String domain);
}
