package com.example.short_link.link.stats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "click_hourly")
@IdClass(ClickHourlyEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickHourlyEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Id
  @Column(name = "hour_start")
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant hourStart;

  @Id
  @Column(name = "age_day")
  private Integer ageDay;

  @Column(nullable = false)
  private int humans;

  @Column(nullable = false)
  private int bots;

  @Column(name = "first_human_at")
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant firstHumanAt;

  @Column(name = "last_human_at")
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant lastHumanAt;

  @Column(name = "last_at", nullable = false)
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant lastAt;

  public static class Key implements Serializable {
    private Long linkId;
    private Instant hourStart;
    private Integer ageDay;

    public Key() {}

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key other)) return false;
      return Objects.equals(linkId, other.linkId)
          && Objects.equals(hourStart, other.hourStart)
          && Objects.equals(ageDay, other.ageDay);
    }

    @Override
    public int hashCode() {
      return Objects.hash(linkId, hourStart, ageDay);
    }
  }
}
