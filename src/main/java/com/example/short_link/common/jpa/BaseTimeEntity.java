package com.example.short_link.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  protected Instant updatedAt;
}
