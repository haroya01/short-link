package com.example.short_link.event.application.read;

import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.domain.EventQuestionEntity;
import java.util.List;

public record EventQuestionView(
    Long id, String type, String label, List<String> options, boolean required) {

  public static EventQuestionView from(EventQuestionEntity question) {
    return new EventQuestionView(
        question.getId(),
        question.getType().name(),
        question.getLabel(),
        EventQuestions.deserializeOptions(question.getOptionsJson()),
        question.isRequired());
  }
}
