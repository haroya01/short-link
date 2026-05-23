package com.example.short_link.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 모든 도메인 entity 의 createdAt 추적 표준. Hibernate 가 INSERT 시 직접 시각을 박는다 — @PrePersist 콜백 boilerplate 와
 * 동등 동작이지만 entity 마다 onCreate() 메서드를 13번 중복할 필요가 없다.
 */
@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  protected Instant createdAt;
}
