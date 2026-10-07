package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.UserDomainBlockEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserDomainBlockRepository extends JpaRepository<UserDomainBlockEntity, Long> {

  List<UserDomainBlockEntity> findByUserIdOrderByDomainAsc(Long userId);

  Optional<UserDomainBlockEntity> findByUserIdAndDomain(Long userId, String domain);

  boolean existsByUserIdAndDomain(Long userId, String domain);

  @Modifying
  @Query("delete from UserDomainBlockEntity b where b.userId = :userId and b.domain = :domain")
  int deleteBlock(@Param("userId") Long userId, @Param("domain") String domain);
}
