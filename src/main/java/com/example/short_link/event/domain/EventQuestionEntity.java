package com.example.short_link.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventQuestionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private Long eventId;

  @Column(nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private QuestionType type;

  @Column(nullable = false, length = 200)
  private String label;

  @Column(name = "options_json", columnDefinition = "TEXT")
  private String optionsJson;

  @Column(nullable = false)
  private boolean required;

  public EventQuestionEntity(
      Long eventId,
      int position,
      QuestionType type,
      String label,
      String optionsJson,
      boolean required) {
    this.eventId = eventId;
    this.position = position;
    this.type = type;
    this.label = label;
    this.optionsJson = optionsJson;
    this.required = required;
  }
}
