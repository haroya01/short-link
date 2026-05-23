package com.example.short_link.link.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "tag",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_tag_user_name",
          columnNames = {"user_id", "name"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(length = 7)
  private String color;

  public TagEntity(Long userId, String name, String color) {
    this.userId = userId;
    this.name = name;
    this.color = color;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void recolor(String color) {
    this.color = color;
  }
}
