package com.example.short_link.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  protected Instant createdAt;
}
