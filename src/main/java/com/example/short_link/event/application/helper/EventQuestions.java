package com.example.short_link.event.application.helper;

import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.QuestionType;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 질문은 options JSON 배열, 답변은 questionId 키의 JSON 객체다. 신청 후 구조를 바꾸면 답변 매핑이 깨진다. */
public final class EventQuestions {

  public static final int MAX_QUESTIONS = 10;
  private static final int MAX_ANSWER_LENGTH = 500;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EventQuestions() {}

  public record QuestionSpec(String type, String label, List<String> options, boolean required) {}

  public static List<EventQuestionEntity> toEntities(Long eventId, List<QuestionSpec> specs) {
    if (specs == null || specs.isEmpty()) return List.of();
    List<EventQuestionEntity> questions = new ArrayList<>(specs.size());
    for (int position = 0; position < specs.size(); position++) {
      QuestionSpec spec = specs.get(position);
      questions.add(
          new EventQuestionEntity(
              eventId,
              position,
              parseType(spec.type()),
              spec.label().trim(),
              serializeOptions(spec.options()),
              spec.required()));
    }
    return questions;
  }

  public static void validateSpecs(List<QuestionSpec> specs) {
    if (specs == null) return;
    if (specs.size() > MAX_QUESTIONS) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "max " + MAX_QUESTIONS);
    }
    for (QuestionSpec spec : specs) {
      QuestionType type = parseType(spec.type());
      if (spec.label() == null || spec.label().isBlank() || spec.label().length() > 200) {
        throw new EventException(EventErrorCode.INVALID_QUESTIONS, "label");
      }
      if (type == QuestionType.SINGLE_CHOICE) {
        List<String> options = spec.options();
        if (options == null || options.size() < 2 || options.size() > 10) {
          throw new EventException(EventErrorCode.INVALID_QUESTIONS, "options 2..10");
        }
        for (String option : options) {
          if (option == null || option.isBlank() || option.length() > 100) {
            throw new EventException(EventErrorCode.INVALID_QUESTIONS, "option");
          }
        }
      }
    }
  }

  public static QuestionType parseType(String raw) {
    try {
      return QuestionType.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "type " + raw);
    }
  }

  public static String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return null;
    try {
      return MAPPER.writeValueAsString(options);
    } catch (JsonProcessingException e) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "options");
    }
  }

  public static List<String> deserializeOptions(String optionsJson) {
    if (optionsJson == null || optionsJson.isBlank()) return List.of();
    try {
      return MAPPER.readValue(optionsJson, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  public static String validateAndSerializeAnswers(
      List<EventQuestionEntity> questions, Map<Long, String> answers) {
    Map<Long, String> given = answers == null ? Map.of() : answers;
    Map<String, String> stored = new LinkedHashMap<>();
    for (EventQuestionEntity question : questions) {
      String answer = given.get(question.getId());
      boolean blank = answer == null || answer.isBlank();
      if (blank) {
        if (question.isRequired()) {
          throw new EventException(EventErrorCode.INVALID_ANSWER, question.getLabel());
        }
        continue;
      }
      if (answer.length() > MAX_ANSWER_LENGTH) {
        throw new EventException(EventErrorCode.INVALID_ANSWER, question.getLabel());
      }
      if (question.getType() == QuestionType.SINGLE_CHOICE
          && !deserializeOptions(question.getOptionsJson()).contains(answer)) {
        throw new EventException(EventErrorCode.INVALID_ANSWER, question.getLabel());
      }
      stored.put(String.valueOf(question.getId()), answer.trim());
    }
    if (stored.isEmpty()) return null;
    try {
      return MAPPER.writeValueAsString(stored);
    } catch (JsonProcessingException e) {
      throw new EventException(EventErrorCode.INVALID_ANSWER, "serialization");
    }
  }

  public static Map<String, String> deserializeAnswers(String answersJson) {
    if (answersJson == null || answersJson.isBlank()) return Map.of();
    try {
      return MAPPER.readValue(answersJson, new TypeReference<Map<String, String>>() {});
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
