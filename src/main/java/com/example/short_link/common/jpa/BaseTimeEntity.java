package com.example.short_link.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * createdAt + updatedAt 둘 다 추적이 필요한 entity 용 superclass. Hibernate 가 INSERT 시 createdAt, UPDATE 시
 * updatedAt 을 자동 박는다. createdAt 만 필요하면 {@link BaseCreatedEntity} 직접 사용.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  protected Instant updatedAt;
}
