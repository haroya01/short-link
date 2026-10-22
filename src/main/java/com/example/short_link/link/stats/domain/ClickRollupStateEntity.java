package com.example.short_link.link.stats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "click_rollup_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickRollupStateEntity {

  @Id private Byte id;

  @Column(name = "rolled_until", nullable = false)
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant rolledUntil;
}
