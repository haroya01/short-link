package com.example.short_link.portability.infrastructure.persistence;

import com.example.short_link.portability.domain.AccountImportEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaAccountImportRepository extends JpaRepository<AccountImportEntity, Long> {

  boolean existsByUserIdAndFinishedAtIsNull(Long userId);

  @Query("select i from AccountImportEntity i where i.userId = :user order by i.id desc")
  List<AccountImportEntity> recent(@Param("user") Long userId, Pageable page);
}
