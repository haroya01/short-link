package com.example.short_link.event.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.QuestionType;
import com.example.short_link.event.exception.EventException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EventQuestionsTest {

  private EventQuestionEntity question(
      long id, QuestionType type, String options, boolean required) {
    EventQuestionEntity entity = new EventQuestionEntity(1L, 0, type, "Q" + id, options, required);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  @Test
  void validateSpecs_capsAtFive() {
    List<QuestionSpec> six =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> new QuestionSpec("SHORT_TEXT", "q" + i, null, false))
            .toList();
    assertThatThrownBy(() -> EventQuestions.validateSpecs(six)).isInstanceOf(EventException.class);
  }

  @Test
  void singleChoice_requiresTwoToTenOptions() {
    assertThatThrownBy(
            () ->
                EventQuestions.validateSpecs(
                    List.of(new QuestionSpec("SINGLE_CHOICE", "메뉴", List.of("한식"), false))))
        .isInstanceOf(EventException.class);
  }

  @Test
  void requiredQuestion_mustBeAnswered() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SHORT_TEXT, null, true));
    assertThatThrownBy(() -> EventQuestions.validateAndSerializeAnswers(questions, Map.of()))
        .isInstanceOf(EventException.class);
  }

  @Test
  void singleChoiceAnswer_mustBeAnOption() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SINGLE_CHOICE, "[\"a\",\"b\"]", true));
    assertThatThrownBy(
            () -> EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "c")))
        .isInstanceOf(EventException.class);
    String json = EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "a"));
    assertThat(EventQuestions.deserializeAnswers(json)).containsEntry("10", "a");
  }

  @Test
  void optionalUnanswered_isOmitted() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SHORT_TEXT, null, false));
    assertThat(EventQuestions.validateAndSerializeAnswers(questions, Map.of())).isNull();
  }
}
